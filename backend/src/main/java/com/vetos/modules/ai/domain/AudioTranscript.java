package com.vetos.modules.ai.domain;

/**
 * @docs/architecture.md Bolum 3 (O -- Open/Closed) ornegindeki AudioTranscript
 * tipi. Transkripsiyon adimi tarayici tarafinda (Web Speech API) yapilir --
 * bu tip sadece SoapGenerationPort'a girdi olarak sunulan metni tasir.
 */
public record AudioTranscript(String text) {}
