package com.vetos.modules.ai.infrastructure.adapter;

import java.util.Map;

/**
 * Ollama istek ayarları (2026-10-25). Varsayılan okuma alanı 2-4 bin parça; hasta geçmişiyle istem uzayınca
 * baştaki Subjective/Objective kesiliyordu. 16384 en kötü durumdaki geçmişi de sığdırır (daha çok VRAM kullanır).
 */
final class OllamaOptions {

    static final int NUM_CTX = 16384;

    private OllamaOptions() {}

    static Map<String, Object> wideContext() {
        return Map.of("num_ctx", NUM_CTX);
    }
}
