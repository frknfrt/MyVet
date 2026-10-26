package com.vetos.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "sku_barcode")
    private String skuBarcode;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    /** TARBIL eslesmesi (spec 2026-10-04 S13): HBSAPP_VACCINE | VETILAC_MEDICINE; TARBIL'den gelmeyen kalemde null. */
    @Column(name = "tarbil_system")
    private String tarbilSystem;

    @Column(name = "tarbil_product_name")
    private String tarbilProductName;

    @Column(name = "tarbil_presentation")
    private String tarbilPresentation;

    /** Kullanim birimi (spec 2026-10-24). ADET disindakilerde paket buyuklugu dolu olur. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockUnit unit = StockUnit.ADET;

    /** Bir kapali adetteki miktar: sisede ml/g, asi kutusunda flakon. ADET'te null. */
    @Column(name = "package_size")
    private BigDecimal packageSize;

    /** Acilmis paketlerde kalan miktar (kullanim biriminde). quantityOnHand yalniz kapali adettir. */
    @Column(name = "opened_quantity", nullable = false)
    private BigDecimal openedQuantity = BigDecimal.ZERO;

    /** AUTO: birim TARBIL takdim seklinden tahmin edildi; MANUAL: kullanici ayarladi, esitleme ezmez. */
    @Column(name = "unit_source", nullable = false)
    private String unitSource = "AUTO";

    /** ML kalemde bir parcanin (pipet/tup) ml'si; stok ve TARBIL ml, parca yalniz giris/gosterim (spec 2026-10-25). */
    @Column(name = "piece_size")
    private BigDecimal pieceSize;

    @Column(name = "piece_label")
    private String pieceLabel;

    /** TARBIL "Acilmis Kalan Miktar Son Kullanma Tarihi"; acik miktar 0 olunca temizlenir (spec 2026-10-25). */
    @Column(name = "opened_expiry_date")
    private LocalDate openedExpiryDate;

    public static InventoryItem create(
        UUID tenantId, UUID branchId, String name, String category, String skuBarcode,
        int initialQuantity, int reorderThreshold, LocalDate expiryDate, String lotNumber, BigDecimal unitCost
    ) {
        InventoryItem item = new InventoryItem();
        item.tenantId = tenantId;
        item.branchId = branchId;
        item.name = name;
        item.category = category;
        item.skuBarcode = skuBarcode;
        item.quantityOnHand = initialQuantity;
        item.reorderThreshold = reorderThreshold;
        item.expiryDate = expiryDate;
        item.lotNumber = lotNumber;
        item.unitCost = unitCost;
        return item;
    }

    public void adjustQuantity(int delta) {
        this.quantityOnHand += delta;
    }

    /** Acilabilir kalemde acik miktar kutu karsiligiyla sayilir: 0 kutu + 19/20 flakon = 0,95 kutu (spec 2026-10-25). */
    public boolean isBelowReorderThreshold() {
        if (!isOpenable()) {
            return quantityOnHand <= reorderThreshold;
        }
        BigDecimal boxes = BigDecimal.valueOf(quantityOnHand).add(openedQuantity.divide(packageSize, 2, java.math.RoundingMode.HALF_UP));
        return boxes.compareTo(BigDecimal.valueOf(reorderThreshold)) <= 0;
    }

    public void updateDetails(String name, String category, int reorderThreshold, BigDecimal unitCost) {
        this.name = name;
        this.category = category;
        this.reorderThreshold = reorderThreshold;
        this.unitCost = unitCost;
    }

    public boolean isUnitManual() {
        return "MANUAL".equals(unitSource);
    }

    /** Paketten kullanilan kalem mi (ML/G/FLAKON ve paket buyuklugu bilinir). */
    public boolean isOpenable() {
        return unit != StockUnit.ADET && packageSize != null && packageSize.signum() > 0;
    }

    public void setStockUnit(StockUnit unit, BigDecimal packageSize, boolean manual) {
        setStockUnit(unit, packageSize, null, null, manual);
    }

    /** Parca (pipet) yalniz ML'de saklanir; diger birimlerde temizlenir. */
    public void setStockUnit(StockUnit unit, BigDecimal packageSize, BigDecimal pieceSize, String pieceLabel, boolean manual) {
        this.unit = unit == null ? StockUnit.ADET : unit;
        this.packageSize = this.unit == StockUnit.ADET ? null : packageSize;
        boolean piece = this.unit == StockUnit.ML && pieceSize != null && pieceSize.signum() > 0;
        this.pieceSize = piece ? pieceSize : null;
        this.pieceLabel = piece ? pieceLabel : null;
        this.unitSource = manual ? "MANUAL" : "AUTO";
    }

    public void setOpenedExpiry(LocalDate date) {
        this.openedExpiryDate = openedQuantity.signum() == 0 ? null : date;
    }

    private void clearOpenedExpiryIfEmpty() {
        if (openedQuantity.signum() == 0) {
            openedExpiryDate = null;
        }
    }

    /**
     * Kullanimi duser (spec 2026-10-24 §3.3). ADET: kapali adetten (kesir yukari yuvarlanir). Paketli kalem: source WHOLE ise once yeni
     * paket acilir; sonra acik miktar yetene kadar paket acilir ve miktar acik miktardan duser. Kapali 0 olsa da paket
     * acilir (kapali eksiye iner; bugunku adet davranisi gibi, fark esitlemede gorunur).
     */
    public StockChange consume(BigDecimal amount, StockSource source) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Kullanım miktarı pozitif olmalı: " + amount);
        }
        if (!isOpenable()) {
            // TARBİL esas (değerlendirme C1): TARBİL'den "0,5" gibi kesirli miktar gelebilir; düşüm hata vermez, yukarı yuvarlanır.
            int whole = amount.setScale(0, java.math.RoundingMode.CEILING).intValueExact();
            quantityOnHand -= whole;
            return new StockChange(-whole, BigDecimal.ZERO);
        }
        int opened = 0;
        BigDecimal before = openedQuantity;
        if (source == StockSource.WHOLE) {
            openPackage();
            opened++;
        }
        while (openedQuantity.compareTo(amount) < 0) {
            openPackage();
            opened++;
        }
        openedQuantity = openedQuantity.subtract(amount);
        clearOpenedExpiryIfEmpty();
        return new StockChange(-opened, openedQuantity.subtract(before));
    }

    /** TARBIL esitlemesi: kapali adet hedefe; acik miktar biliniyorsa hedefe (null = bilinmiyor, degismez). */
    public StockChange syncTo(int closed, BigDecimal openedOrNull) {
        int closedDelta = closed - quantityOnHand;
        BigDecimal openedDelta = BigDecimal.ZERO;
        quantityOnHand = closed;
        if (openedOrNull != null) {
            openedDelta = openedOrNull.subtract(openedQuantity);
            openedQuantity = openedOrNull;
            clearOpenedExpiryIfEmpty();
        }
        return new StockChange(closedDelta, openedDelta);
    }

    /**
     * Elle acik miktar duzeltmesi (kirilma, dokulme; spec 2026-10-25 §4.4/14). Yalniz acilabilir kalemde; sonuc eksi olamaz,
     * |fark| en fazla 100.000. 0'a inerse acik urun tarihi temizlenir.
     */
    public StockChange adjustOpened(BigDecimal delta) {
        if (!isOpenable() || delta == null || delta.signum() == 0 || delta.abs().compareTo(new BigDecimal("100000")) > 0) {
            throw new com.vetos.modules.inventory.domain.exception.InvalidStockUnitException();
        }
        BigDecimal next = openedQuantity.add(delta);
        if (next.signum() < 0) {
            throw new com.vetos.modules.inventory.domain.exception.OpenedQuantityNegativeException();
        }
        openedQuantity = next;
        clearOpenedExpiryIfEmpty();
        return new StockChange(0, delta);
    }

    private void openPackage() {
        quantityOnHand -= 1;
        openedQuantity = openedQuantity.add(packageSize);
    }

    public void linkTarbil(String system, String productName, String presentation) {
        this.tarbilSystem = system;
        this.tarbilProductName = productName;
        this.tarbilPresentation = presentation;
    }
}
