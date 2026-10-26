package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eski eklenti (0.2.x) partStockRead göndermez ama Parça Stok satırlarını gönderir: o satırlar geldiyse tablo okunmuş
 * sayılır, yoksa yalnız parça stokta olan aşı lotu eşitlemede sıfırlanırdı (değerlendirme I2).
 */
class StockSnapshotRequestTest {

    private static StockSnapshotLineRequest line(boolean part) {
        return new StockSnapshotLineRequest("Biocan R", "Flakon", "665932", null, 18, null, part, null);
    }

    @Test
    void oldExtensionThatSentPartStockLines_countsAsPartStockRead() {
        assertThat(new StockSnapshotRequest(TarbilStockSystem.HBSAPP_VACCINE, List.of(line(true)), null).isPartStockRead()).isTrue();
    }

    @Test
    void oldExtensionWithoutPartStockLines_doesNotCountAsRead() {
        assertThat(new StockSnapshotRequest(TarbilStockSystem.HBSAPP_VACCINE, List.of(line(false)), null).isPartStockRead()).isFalse();
    }

    @Test
    void explicitFlagFromNewExtensionWins() {
        assertThat(new StockSnapshotRequest(TarbilStockSystem.HBSAPP_VACCINE, List.of(line(true)), false).isPartStockRead()).isFalse();
    }

    @Test
    void snapshotRequest_mapsOpenedExpiryDate() {
        var l = new StockSnapshotLineRequest("CLAVON", "50 ml'lik", "63016", null, 0, new java.math.BigDecimal("49"), false, null,
            java.time.LocalDate.of(2026, 11, 20));
        var input = new StockSnapshotRequest(TarbilStockSystem.VETILAC_MEDICINE, List.of(l), null).toInputs().get(0);
        assertThat(input.openedExpiryDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 20));
    }
}
