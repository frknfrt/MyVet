package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingDeductionServiceTest {

    @Mock private PendingStockDeductionRepository repository;
    @Mock private InventoryItemRepository items;
    @Mock private RecordStockMovementUseCase movements;

    private final UUID tenant = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final List<PendingStockDeduction> rows = new ArrayList<>();
    private PendingDeductionService service;

    private UUID item(String name, String lot) {
        InventoryItem i = InventoryItem.create(tenant, UUID.randomUUID(), name, "İlaç", null, 10, 0, LocalDate.of(2027, 1, 1), lot, BigDecimal.ONE);
        ReflectionTestUtils.setField(i, "id", UUID.randomUUID()); // id @GeneratedValue
        lenient().when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i.getId();
    }

    private PendingStockDeduction pending(UUID itemId, int qty) {
        PendingStockDeduction p = PendingStockDeduction.open(tenant, itemId, qty, StockReferenceType.ENCOUNTER, ref);
        rows.add(p);
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new PendingDeductionService(repository, items, movements);
        lenient().when(repository.findByReference(StockReferenceType.ENCOUNTER, ref)).thenReturn(rows);
        lenient().when(repository.save(any())).thenAnswer(a -> a.getArgument(0));
        lenient().when(repository.claimForSettlement(any(), anyInt(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void concurrentSettlementThatLostTheClaimWritesNoMovement() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);
        when(repository.claimForSettlement(eq(p.getId()), anyInt(), any(), any(), any())).thenReturn(false);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.TARBIL, staff);

        assertThat(r.settledCount()).isZero();
        verifyNoInteractions(movements);
    }

    @Test
    void settlesAllPlannedQuantitiesWhenNothingIsReported() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.TARBIL, staff);

        assertThat(r.settledCount()).isEqualTo(1);
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        verify(movements).execute(a, StockMovementType.OUT, 2, StockReferenceType.ENCOUNTER, ref);
    }

    @Test
    void deductsReportedQuantityAndKeepsUnreportedLinesPending() {
        UUID a = item("Synulox", "S1");
        UUID b = item("Metacam", "M7");
        PendingStockDeduction pa = pending(a, 2);
        PendingStockDeduction pb = pending(b, 1);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref,
            List.of(new ReportedDeductionLine("Synulox", "S1", 3), new ReportedDeductionLine("Baytril", "B9", 1)),
            DeductionSettlement.TARBIL, staff);

        assertThat(pa.getSettledQuantity()).isEqualTo(3);
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.PENDING);
        assertThat(r.unmatched()).containsExactly(new ReportedDeductionLine("Baytril", "B9", 1));
        verify(movements).execute(a, StockMovementType.OUT, 3, StockReferenceType.ENCOUNTER, ref);
        verify(movements, never()).execute(eq(b), any(), anyInt(), any(), any());
    }

    @Test
    void matchesSerialIgnoringCaseAndSpaces() {
        UUID a = item("Synulox", "ab 12");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", " AB12 ", 1)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }

    @Test
    void matchesSerialRegardlessOfTurkishDottedI() {
        UUID a = item("Synulox", "ki7");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", "KI7", 1)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }

    @Test
    void doesNotDeductAlreadySettledLinesAgain() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);
        p.settle(2, DeductionSettlement.VETLY_ONLY, staff, Instant.EPOCH);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.TARBIL, staff);

        assertThat(r.settledCount()).isZero();
        verifyNoInteractions(movements);
    }

    @Test
    void cancelLeavesDeductedLinesAlone() {
        UUID a = item("Synulox", "S1");
        UUID b = item("Metacam", "M7");
        PendingStockDeduction pa = pending(a, 1);
        PendingStockDeduction pb = pending(b, 1);
        pb.settle(1, DeductionSettlement.TARBIL, staff, Instant.EPOCH);

        assertThat(service.cancel(StockReferenceType.ENCOUNTER, ref)).isTrue();
        assertThat(pa.getStatus()).isEqualTo(PendingDeductionStatus.CANCELLED);
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        assertThat(service.hasDeducted(StockReferenceType.ENCOUNTER, ref)).isTrue();
    }

    @Test
    void openIsIdempotentPerItem() {
        UUID a = item("Synulox", "S1");
        pending(a, 2);

        service.open(a, 2, StockReferenceType.ENCOUNTER, ref);

        verify(repository, never()).save(any());
    }
}
