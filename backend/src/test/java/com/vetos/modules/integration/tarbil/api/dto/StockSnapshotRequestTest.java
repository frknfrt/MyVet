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
}
