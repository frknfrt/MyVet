package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockComparison;
import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockBranchMissingException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Eklenti stok sayfasi acilinca: TARBIL satirlari Vetly stoguyla karsilastirilir, HICBIR SEY kaydedilmez. */
@ExtendWith(MockitoExtension.class)
class CompareStockSnapshotUseCaseTest {

    @Mock private StaffUserLookupPort staffUserLookupPort;
    @Mock private TarbilStockSyncPort stockSyncPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private static StockSnapshotLineInput line(String product, String lot, int qty) {
        return new StockSnapshotLineInput(product, "Kutu", lot, null, qty, null);
    }

    private CompareStockSnapshotUseCase useCase() {
        return new CompareStockSnapshotUseCase(staffUserLookupPort, stockSyncPort);
    }

    @Test
    void should_countNewDifferentAndMatchedProducts_againstTheStaffBranchStock() {
        when(staffUserLookupPort.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr", StaffRole.VET, branchId));
        when(stockSyncPort.listForBranch(branchId)).thenReturn(List.of(
            new InventoryStockView(UUID.randomUUID(), "Rabisin", "R9", null, 4, null),
            new InventoryStockView(UUID.randomUUID(), "Drontal", "D1", null, 8, null)));

        StockComparison c = useCase().execute(tenantId, staffId, TarbilStockSystem.VETILAC_MEDICINE, List.of(
            line("Biocan R", "B1", 3),      // Vetly'de yok
            line("Rabisin", "R9", 10),      // miktar farkli
            line("Drontal", "D1", 5),       // ayni urun+lot iki satirda: 5 + 3 = 8 -> eslesti
            line("Drontal", "D1", 3)));

        assertThat(c).isEqualTo(new StockComparison(1, 1, 1));
    }

    @Test
    void should_reportNothing_when_tarbilListIsEmpty() {
        when(staffUserLookupPort.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr", StaffRole.VET, branchId));
        when(stockSyncPort.listForBranch(branchId)).thenReturn(List.of());

        assertThat(useCase().execute(tenantId, staffId, TarbilStockSystem.HBSAPP_VACCINE, List.of())).isEqualTo(new StockComparison(0, 0, 0));
    }

    @Test
    void should_refuse_when_staffHasNoBranch() {
        when(staffUserLookupPort.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr", StaffRole.VET, null));

        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilStockSystem.HBSAPP_VACCINE, List.of(line("A", "L", 1))))
            .isInstanceOf(TarbilStockBranchMissingException.class);
    }
}
