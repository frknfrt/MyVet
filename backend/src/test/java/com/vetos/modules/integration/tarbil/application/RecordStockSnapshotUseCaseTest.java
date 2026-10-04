package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilStockSnapshotException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordStockSnapshotUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    private final UUID tenantId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void should_saveNumberedTrimmedLines() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordStockSnapshotUseCase(repository).execute(tenantId, UUID.randomUUID(), TarbilStockSystem.VETILAC_MEDICINE, List.of(
            new StockSnapshotLineInput("  Drontal ", " Kutu ", " L1 ", LocalDate.of(2027, 1, 31), 3, new BigDecimal("0.5")),
            new StockSnapshotLineInput("Rabisin", null, "", null, 0, null)));

        ArgumentCaptor<TarbilStockSnapshot> snapshot = ArgumentCaptor.forClass(TarbilStockSnapshot.class);
        verify(repository).save(snapshot.capture());
        assertThat(snapshot.getValue().getTenantId()).isEqualTo(tenantId);
        ArgumentCaptor<List<TarbilStockSnapshotLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(repository).saveLines(lines.capture());
        assertThat(lines.getValue()).extracting(TarbilStockSnapshotLine::getLineNo).containsExactly(1, 2);
        assertThat(lines.getValue().get(0).getProductName()).isEqualTo("Drontal");
        assertThat(lines.getValue().get(0).getLotNumber()).isEqualTo("L1");
        assertThat(lines.getValue().get(1).getLotNumber()).isNull();
    }

    @Test
    void should_reject_when_tooManyLinesOrBlankName() {
        var useCase = new RecordStockSnapshotUseCase(repository);
        List<StockSnapshotLineInput> tooMany = Collections.nCopies(501, new StockSnapshotLineInput("A", null, "L", null, 1, null));

        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE, tooMany))
            .isInstanceOf(InvalidTarbilStockSnapshotException.class);
        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE,
            List.of(new StockSnapshotLineInput(" ", null, "L", null, 1, null))))
            .isInstanceOf(InvalidTarbilStockSnapshotException.class);
    }
}
