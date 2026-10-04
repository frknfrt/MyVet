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
 * Stoktan secilen asi (spec 2026-10-04 P2 S3.3): uygulaninca 1 OUT, iptalde (yalniz dusulmusse) 1 IN.
 * Ayni asi kaydi icin her yonde en fazla bir hareket (V68 tekil indeks es zamanli istekte de korur); stok 0'da eksiye
 * dusmez ve kayit yine olusur. Kalem asi degilse ya da serisi asi kaydinin serisiyle ayni degilse dusulmez.
 */
@Service
@RequiredArgsConstructor
public class ApplyVaccinationStockUseCase {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @Transactional
    public void administered(UUID vaccinationRecordId, UUID inventoryItemId, String lotNumber) {
        if (inventoryItemId == null
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)) {
            return;
        }
        items.findById(inventoryItemId)
            .filter(i -> i.getQuantityOnHand() > 0 && isVaccine(i) && serial(i.getLotNumber()).equals(serial(lotNumber)))
            .ifPresent(item -> {
                item.adjustQuantity(-1);
                items.save(item);
                movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.OUT, 1,
                    StockReferenceType.VACCINATION, vaccinationRecordId));
            });
    }

    @Transactional
    public void cancelled(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null
            || !movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)
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
