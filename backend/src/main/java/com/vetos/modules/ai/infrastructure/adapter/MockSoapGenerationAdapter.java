package com.vetos.modules.ai.infrastructure.adapter;

import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import org.springframework.stereotype.Component;

/**
 * Gercek LLM API anahtari saglanana kadar kullanilan yer tutucu adaptor
 * (@docs/architecture.md TARBIL/MockTarbilAdapter ile ayni desen). Transkripti
 * NLP ile yapilandirmaz -- ham metni Subjective alanina aktarip durumu acikca
 * belirtir. Gercek saglayici eklendiginde (orn. AnthropicSoapAdapter) sadece
 * bu sinif degisir, SoapGenerationPort sozlesmesi ve cagiran kod aynen kalir.
 */
@Component
class MockSoapGenerationAdapter implements SoapGenerationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- bu, dikte edilen/yazilan metnin oldugu gibi Subjective alanina "
        + "aktarilmasidir. Gercek bir LLM API anahtari eklendiginde bu adaptor degisecek ve SOAP alanlari "
        + "otomatik olarak yapilandirilacak. Su an icin Objective/Assessment/Plan alanlarini elle doldurun.]";

    @Override
    public SoapDraft generate(AudioTranscript transcript) {
        String text = transcript.text() == null ? "" : transcript.text().trim();
        String subjective = text.isEmpty() ? STATUS_NOTE : text + "\n\n" + STATUS_NOTE;
        return new SoapDraft(subjective, "", "", "", false);
    }
}
