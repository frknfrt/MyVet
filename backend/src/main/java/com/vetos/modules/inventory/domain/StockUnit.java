package com.vetos.modules.inventory.domain;

/**
 * Kullanim birimi (spec 2026-10-24): ADET bolunmez; ML/G sise/tupten, FLAKON asi kutusundan kullanilir.
 * TABLET (spec 2026-10-25): kutudan tablet tablet kullanilir; TARBIL'e tablet sayisi yazilir.
 */
public enum StockUnit { ADET, ML, G, FLAKON, TABLET }
