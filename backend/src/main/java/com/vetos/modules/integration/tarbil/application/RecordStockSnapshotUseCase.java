package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilStockSnapshotException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Eklentinin TARBIL stok sayfasindan okudugu tabloyu saklar; Vetly stoguna HENUZ dokunmaz (hekim web'den isler). */
@Service
@RequiredArgsConstructor
public class RecordStockSnapshotUseCase {

    static final int MAX_LINES = 500;

    private final TarbilStockSnapshotRepository repository;

    @Transactional
    public UUID execute(UUID tenantId, UUID staffId, TarbilStockSystem system, List<StockSnapshotLineInput> lines) {
        if (system == null) {
            throw new InvalidTarbilStockSnapshotException("sistem bos");
        }
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidTarbilStockSnapshotException("satir sayisi 1-" + MAX_LINES + " olmali");
        }
        for (StockSnapshotLineInput l : lines) {
            if (l.productName() == null || l.productName().isBlank()) {
                throw new InvalidTarbilStockSnapshotException("urun adi bos");
            }
            if (l.quantity() < 0) {
                throw new InvalidTarbilStockSnapshotException("miktar negatif");
            }
        }
        TarbilStockSnapshot snapshot = repository.save(TarbilStockSnapshot.take(tenantId, system, staffId, Instant.now()));
        List<TarbilStockSnapshotLine> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            StockSnapshotLineInput l = lines.get(i);
            rows.add(TarbilStockSnapshotLine.of(snapshot.getId(), tenantId, i + 1, l.productName().trim(), trimToNull(l.presentation()),
                trimToNull(l.lotNumber()), l.expiryDate(), l.quantity(), l.openedQuantity()));
        }
        repository.saveLines(rows);
        return snapshot.getId();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
