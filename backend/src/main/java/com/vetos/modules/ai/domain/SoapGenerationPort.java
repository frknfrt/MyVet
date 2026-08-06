package com.vetos.modules.ai.domain;

/**
 * @docs/architecture.md Bolum 3 (O -- Open/Closed) -- yeni bir AI saglayici
 * (Anthropic, OpenAI, self-hosted...) eklemek icin tek yapilan: bu arayuzu
 * implemente eden yeni bir @Component yazmak. Gercek API anahtari
 * gelene kadar MockSoapGenerationAdapter kullanilir (@docs/architecture.md
 * TARBIL/MockTarbilAdapter ile ayni desen).
 */
public interface SoapGenerationPort {
    SoapDraft generate(AudioTranscript transcript);
}
