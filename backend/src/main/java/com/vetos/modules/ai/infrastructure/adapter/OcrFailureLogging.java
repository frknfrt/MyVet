package com.vetos.modules.ai.infrastructure.adapter;

import org.springframework.web.client.RestClientResponseException;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma -- REVIZE
 * (2026-10-31). Daha once OCR adaptorlerinin catch(Exception e) bloklari
 * SADECE e.getMessage() logluyordu; bir Claude/Ollama 400 hatasinda bu genelde
 * anlamsiz bir ozet olup GERCEK sebebi (ornegin Anthropic'in donderdugi
 * "media_type gecersiz" aciklamasini iceren HTTP govdesi) gizliyordu -- bir
 * sonraki benzer arizada tekrar kullaniciyi/sunucu loglarini bekletmek yerine,
 * HTTP govdesi + stack trace DOGRUDAN loglansin diye bu yardimci eklendi.
 */
final class OcrFailureLogging {

    private OcrFailureLogging() {
    }

    /** Claude/Ollama cagrisinin basarisiz olma sebebini -- varsa HTTP govdesiyle birlikte -- ozetler. */
    static String describe(Exception e) {
        if (e instanceof RestClientResponseException httpError) {
            return httpError.getStatusCode() + " " + httpError.getResponseBodyAsString();
        }
        return e.getMessage();
    }
}
