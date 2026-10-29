package com.vetos.modules.ai.domain;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29).
 * Tek bir dosyanin ham bayti + content-type'i. Ollama /api/generate "images"
 * alani icin content-type onemli degil (sadece ham bayt yeterli), ama Claude
 * Messages API gorsel bloklarinda "media_type" ZORUNLU -- bu yuzden iki
 * saglayicinin da kullanabilmesi icin content-type burada tasinir.
 */
public record OcrImageInput(String contentType, byte[] content) {}
