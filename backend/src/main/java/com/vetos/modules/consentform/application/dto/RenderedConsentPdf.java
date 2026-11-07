package com.vetos.modules.consentform.application.dto;

import java.time.LocalDate;

/** Uretilen PDF + indirme dosya adi icin baslik ve tarih. */
public record RenderedConsentPdf(byte[] content, String title, LocalDate date) {}
