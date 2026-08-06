package com.vetos.modules.integration.efatura.domain;

/**
 * GIB'e kayitli mukellef (isletme) aliciya E_FATURA, bireysel (Pet Owner)
 * aliciya E_ARSIV kesilir. Bu ortamda gercek GIB mukellef sorgulama servisi
 * baglanmadigindan MVP'de tum aliciler bireysel kabul edilip E_ARSIV
 * kullanilir; mukellef sorgusu eklendiginde MockEInvoiceGatewayAdapter'in
 * yerini alacak gercek adaptorde bu ayrim yapilacaktir.
 */
public enum EInvoiceDocumentType { E_ARSIV, E_FATURA }
