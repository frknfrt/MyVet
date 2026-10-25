package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Stoktan secilen asi (spec 2026-10-09 S5.2): uygulaninca stok dusmez, TARBIL dusumunu bekler (1 adet); OUT, TARBIL
 * onayi gelince PendingDeductionService ile yazilir (V77: bir asiya bir OUT). Iptalde bekleyen kapanir; TARBIL'de dusmusse
 * iade yok (TARBIL esastir). Eski modelle dusulmus kayitlarda (OUT var, bekleyen yok) iptal 1 IN yazar.
 * Kalem asi degilse ya da serisi asi kaydinin serisiyle ayni degilse bekleme acilmaz.
 */
@Service
@RequiredArgsConstructor
public class ApplyVaccinationStockUseCase {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final PendingDeductionService pending;

    @Transactional
    public void administered(UUID vaccinationRecordId, UUID inventoryItemId, String lotNumber) {
        if (inventoryItemId == null
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)) {
            return;
        }
        items.findById(inventoryItemId)
            .filter(i -> isVaccine(i) && serial(i.getLotNumber()).equals(serial(lotNumber)))
            .ifPresent(item -> pending.open(inventoryItemId, java.math.BigDecimal.ONE, StockReferenceType.VACCINATION, vaccinationRecordId));
    }

    @Transactional
    public void cancelled(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null) {
            return;
        }
        if (!pending.lines(StockReferenceType.VACCINATION, vaccinationRecordId).isEmpty()) {
            pending.cancel(StockReferenceType.VACCINATION, vaccinationRecordId);
            return;
        }
        if (!movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.IN)) {
            return;
        }
        items.findById(inventoryItemId).ifPresent(item -> {
            item.adjustQuantity(1);
            items.save(item);
            movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.IN, 1,
                StockReferenceType.VACCINATION, vaccinationRecordId));
        });
    }

    private static final Locale TR = Locale.forLanguageTag("tr");

    /** P1a TARBIL asi kalemi ya da kategorisi "Asi" olan kalem. */
    private static boolean isVaccine(InventoryItem item) {
        if ("HBSAPP_VACCINE".equals(item.getTarbilSystem())) {
            return true;
        }
        String category = item.getCategory() == null ? "" : item.getCategory().trim().toLowerCase(TR).replace('ş', 's').replace('ı', 'i');
        return category.equals("asi");
    }

    private static String serial(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(TR);
    }
}
