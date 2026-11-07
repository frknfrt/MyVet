package com.vetos.modules.consentform.domain;

import java.time.Instant;
import java.util.Map;

/**
 * PDF'e basilacak veri. content null ise belge icerigi dogrulanamamistir (bozuk kayit).
 * signedAt null ise bos sablon cikti demektir (imza kutusu bos, ozet satiri yok).
 */
public record ConsentFormPdfData(
    String clinicName,
    String title,
    FormTemplateContent content,
    Map<String, Object> values,
    String signerLine,
    Instant signedAt,
    byte[] signaturePng,
    String digestShort,
    boolean voided,
    String voidReason,
    boolean integrityOk
) {}
