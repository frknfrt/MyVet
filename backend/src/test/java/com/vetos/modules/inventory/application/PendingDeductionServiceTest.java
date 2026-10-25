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
import com.vetos.modules.inventory.domain.StockSource;
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
        PendingStockDeduction p = PendingStockDeduction.open(tenant, itemId, BigDecimal.valueOf(qty), StockReferenceType.ENCOUNTER, ref);
        rows.add(p);
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new PendingDeductionService(repository, items, movements);
        lenient().when(repository.findByReference(StockReferenceType.ENCOUNTER, ref)).thenReturn(rows);
        lenient().when(repository.save(any())).thenAnswer(a -> a.getArgument(0));
        lenient().when(repository.claimForSettlement(any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void concurrentSettlementThatLostTheClaimWritesNoMovement() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);
        when(repository.claimForSettlement(eq(p.getId()), any(), any(), any(), any(), any())).thenReturn(false);

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
        verify(movements).consume(a, new BigDecimal("2"), null, StockReferenceType.ENCOUNTER, ref);
    }

    @Test
    void deductsReportedQuantityAndKeepsUnreportedLinesPending() {
        UUID a = item("Synulox", "S1");
        UUID b = item("Metacam", "M7");
        PendingStockDeduction pa = pending(a, 2);
        PendingStockDeduction pb = pending(b, 1);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref,
            List.of(new ReportedDeductionLine("Synulox", "S1", new BigDecimal("3"), null), new ReportedDeductionLine("Baytril", "B9", new BigDecimal("1"), null)),
            DeductionSettlement.TARBIL, staff);

        assertThat(pa.getSettledQuantity()).isEqualByComparingTo("3");
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.PENDING);
        assertThat(r.unmatched()).containsExactly(new ReportedDeductionLine("Baytril", "B9", new BigDecimal("1"), null));
        verify(movements).consume(a, new BigDecimal("3"), null, StockReferenceType.ENCOUNTER, ref);
        verify(movements, never()).consume(eq(b), any(), any(), any(), any());
    }

    @Test
    void matchesSerialIgnoringCaseAndSpaces() {
        UUID a = item("Synulox", "ab 12");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", " AB12 ", new BigDecimal("1"), null)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }

    @Test
    void matchesSerialRegardlessOfTurkishDottedI() {
        UUID a = item("Synulox", "ki7");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", "KI7", new BigDecimal("1"), null)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }

    @Test
    void doesNotDeductAlreadySettledLinesAgain() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);
        p.settle(BigDecimal.valueOf(2), null, DeductionSettlement.VETLY_ONLY, staff, Instant.EPOCH);

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
        pb.settle(BigDecimal.valueOf(1), null, DeductionSettlement.TARBIL, staff, Instant.EPOCH);

        assertThat(service.cancel(StockReferenceType.ENCOUNTER, ref)).isTrue();
        assertThat(pa.getStatus()).isEqualTo(PendingDeductionStatus.CANCELLED);
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        assertThat(service.hasDeducted(StockReferenceType.ENCOUNTER, ref)).isTrue();
    }

    @Test
    void openIsIdempotentPerItem() {
        UUID a = item("Synulox", "S1");
        pending(a, 2);

        service.open(a, BigDecimal.valueOf(2), StockReferenceType.ENCOUNTER, ref);

        verify(repository, never()).save(any());
    }

    @Test
    void settle_mlFromOpen_passesSourceAndAmount() {
        UUID a = item("Clavon", "63016");
        PendingStockDeduction p = PendingStockDeduction.open(tenant, a, new BigDecimal("1.5"), StockReferenceType.ENCOUNTER, ref);
        rows.add(p);

        service.settle(StockReferenceType.ENCOUNTER, ref,
            List.of(new ReportedDeductionLine("CLAVON = Clavon", "63016", new BigDecimal("1.5"), StockSource.OPEN)), DeductionSettlement.TARBIL, staff);

        verify(movements).consume(a, new BigDecimal("1.5"), StockSource.OPEN, StockReferenceType.ENCOUNTER, ref);
        assertThat(p.getSettledQuantity()).isEqualByComparingTo("1.5");
        assertThat(p.getSettledSource()).isEqualTo(StockSource.OPEN);
    }

    @Test
    void settle_reportedWholeWins_overVetlyRule() {
        UUID a = item("Clavon", "63016");
        pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref,
            List.of(new ReportedDeductionLine("CLAVON = Clavon", "63016", BigDecimal.ONE, StockSource.WHOLE)), DeductionSettlement.TARBIL, staff);

        verify(movements).consume(a, BigDecimal.ONE, StockSource.WHOLE, StockReferenceType.ENCOUNTER, ref);
    }

    @Test
    void settle_withoutReport_usesVetlyRule() {
        UUID a = item("Clavon", "63016");
        PendingStockDeduction p = PendingStockDeduction.open(tenant, a, new BigDecimal("2.5"), StockReferenceType.ENCOUNTER, ref);
        rows.add(p);

        service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.VETLY_ONLY, staff);

        verify(movements).consume(a, new BigDecimal("2.5"), null, StockReferenceType.ENCOUNTER, ref);
        assertThat(p.getSettledSource()).isNull();
    }
}
