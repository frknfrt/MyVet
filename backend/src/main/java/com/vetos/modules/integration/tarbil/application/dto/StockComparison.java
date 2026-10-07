package com.vetos.modules.integration.tarbil.application.dto;

/** TARBIL stok sayfasi ile Vetly stogunun karsilastirmasi (urun+lot gruplari sayilir). */
public record StockComparison(int newCount, int quantityDiffersCount, int matchedCount) {}
