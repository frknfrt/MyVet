package com.vetos.modules.ai.domain;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). Vision OCR'dan okunan tek parametre satiri. */
public record OcrLabItem(String parameterName, String value, String unit, String referenceRange) {}
