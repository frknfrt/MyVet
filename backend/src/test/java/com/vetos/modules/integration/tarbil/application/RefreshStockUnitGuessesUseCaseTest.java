package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vetly ile TARBİL'de aynı görünen (seçilemeyen) ürünler de birim tahmini alır; yoksa eski ml'li ürün "adet" kalır ve
 * eklenti TARBİL'e yanlış Miktar yazar (değerlendirme I4).
 */
@ExtendWith(MockitoExtension.class)
class RefreshStockUnitGuessesUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    @Mock private StaffUserLookupPort staff;
    @Mock private TarbilStockSyncPort stockSyncPort;

    private final UUID tenant = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();

    @Test
    void guessesTheUnitOfEveryMatchedItemOnce_andSkipsNewProducts() {
        TarbilStockSnapshot snapshot = TarbilStockSnapshot.take(tenant, TarbilStockSystem.VETILAC_MEDICINE, staffId, Instant.now());
        UUID id = UUID.randomUUID();
        when(repository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(repository.findLines(snapshot.getId())).thenReturn(List.of(
            TarbilStockSnapshotLine.of(snapshot.getId(), tenant, 1, "TENIDUR", "100 ml'lik şişe", "251227", null, 2, BigDecimal.ZERO),
            TarbilStockSnapshotLine.of(snapshot.getId(), tenant, 2, "YENI URUN", "Kutu", "X1", null, 1, null)));
        when(staff.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr", StaffRole.VET, branch));
        when(stockSyncPort.listForBranch(branch)).thenReturn(List.of(
            new InventoryStockView(id, "Tenidur", "251227", null, 2, "TENIDUR", BigDecimal.ZERO, "ADET")));

        new RefreshStockUnitGuessesUseCase(repository, staff, stockSyncPort).execute(staffId, snapshot.getId());

        verify(stockSyncPort, times(1)).refreshUnitGuess(eq(id), eq("VETILAC_MEDICINE"), eq("100 ml'lik şişe"), any());
        verify(stockSyncPort, times(1)).refreshUnitGuess(any(), any(), any(), any());
    }

    @Test
    void staffWithoutBranch_changesNothing() {
        TarbilStockSnapshot snapshot = TarbilStockSnapshot.take(tenant, TarbilStockSystem.VETILAC_MEDICINE, staffId, Instant.now());
        when(repository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(staff.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr", StaffRole.VET, null));

        new RefreshStockUnitGuessesUseCase(repository, staff, stockSyncPort).execute(staffId, snapshot.getId());

        verify(stockSyncPort, never()).refreshUnitGuess(any(), any(), any(), any());
    }
}
