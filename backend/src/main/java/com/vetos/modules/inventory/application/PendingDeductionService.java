package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bekleyen TARBIL dusumu kurallari (spec 2026-10-09 S4-S5), tek yazma noktasi. reported == null -> tum bekleyenler
 * planlanan adetle; aksi halde her bildirilen satir bir bekleyenle seri no uzerinden (bosluksuz, buyuk harf) eslesir.
 */
@Service
@RequiredArgsConstructor
public class PendingDeductionService {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final PendingStockDeductionRepository repository;
    private final InventoryItemRepository items;
    private final RecordStockMovementUseCase movements;

    @Transactional
    public void open(UUID inventoryItemId, int quantity, StockReferenceType type, UUID referenceId) {
        boolean exists = repository.findByReference(type, referenceId).stream()
            .anyMatch(p -> p.getInventoryItemId().equals(inventoryItemId));
        if (exists || quantity <= 0) {
            return;
        }
        repository.save(PendingStockDeduction.open(TenantContext.current(), inventoryItemId, quantity, type, referenceId));
    }

    @Transactional(readOnly = true)
    public List<PendingDeductionLine> lines(StockReferenceType type, UUID referenceId) {
        return repository.findByReference(type, referenceId).stream().map(this::toLine).toList();
    }

    @Transactional
    public DeductionSettlementResult settle(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported,
                                            DeductionSettlement via, UUID staffId) {
        List<PendingStockDeduction> pending = repository.findByReference(type, referenceId).stream()
            .filter(p -> p.getStatus() == PendingDeductionStatus.PENDING)
            .collect(Collectors.toCollection(ArrayList::new));
        Instant now = Instant.now();
        int settled = 0;
        if (reported == null) {
            for (PendingStockDeduction p : pending) {
                settled += deduct(p, p.getQuantity(), via, staffId, now);
            }
            return new DeductionSettlementResult(settled, List.of());
        }
        List<ReportedDeductionLine> unmatched = new ArrayList<>();
        for (ReportedDeductionLine line : reported) {
            String reportedSerial = serial(line.serialNumber());
            Optional<PendingStockDeduction> match = reportedSerial.isEmpty() ? Optional.empty() : pending.stream()
                .filter(p -> serial(lotOf(p)).equals(reportedSerial))
                .findFirst();
            if (match.isPresent() && line.quantity() > 0) {
                settled += deduct(match.get(), line.quantity(), via, staffId, now);
                pending.remove(match.get());
            } else {
                unmatched.add(line);
            }
        }
        return new DeductionSettlementResult(settled, unmatched);
    }

    /** @return true ise en az bir bekleyen iptal edildi; dusulmus satirlara dokunulmaz. */
    @Transactional
    public boolean cancel(StockReferenceType type, UUID referenceId) {
        boolean any = false;
        for (PendingStockDeduction p : repository.findByReference(type, referenceId)) {
            if (p.cancel()) {
                repository.save(p);
                any = true;
            }
        }
        return any;
    }

    @Transactional(readOnly = true)
    public boolean hasDeducted(StockReferenceType type, UUID referenceId) {
        return repository.findByReference(type, referenceId).stream().anyMatch(p -> p.getStatus() == PendingDeductionStatus.DEDUCTED);
    }

    /** Kosullu UPDATE'i kazanan istek hareketi yazar; kaybeden (es zamanli onay) hicbir sey yapmaz. */
    private int deduct(PendingStockDeduction p, int quantity, DeductionSettlement via, UUID staffId, Instant now) {
        if (p.getStatus() != PendingDeductionStatus.PENDING
            || !repository.claimForSettlement(p.getId(), quantity, via, staffId, now)) {
            return 0;
        }
        p.settle(quantity, via, staffId, now);
        movements.execute(p.getInventoryItemId(), StockMovementType.OUT, quantity, p.getReferenceType(), p.getReferenceId());
        return 1;
    }

    private String lotOf(PendingStockDeduction p) {
        return items.findById(p.getInventoryItemId()).map(InventoryItem::getLotNumber).orElse(null);
    }

    private PendingDeductionLine toLine(PendingStockDeduction p) {
        Optional<InventoryItem> item = items.findById(p.getInventoryItemId());
        return new PendingDeductionLine(p.getId(), p.getInventoryItemId(),
            item.map(InventoryItem::getName).orElse("—"), item.map(InventoryItem::getTarbilProductName).orElse(null),
            item.map(InventoryItem::getLotNumber).orElse(null), p.getQuantity(), p.getStatus(), p.getSettledQuantity());
    }

    static String serial(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(TR);
    }
}
