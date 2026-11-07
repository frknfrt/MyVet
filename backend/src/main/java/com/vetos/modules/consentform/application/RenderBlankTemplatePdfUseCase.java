package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.FormTemplateDetail;
import com.vetos.modules.consentform.application.dto.RenderedConsentPdf;
import com.vetos.modules.consentform.domain.ConsentFormPdfData;
import com.vetos.modules.consentform.domain.ConsentFormPdfPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** Sablonun doldurulmamis (bos) PDF'i: elle doldurulup imzalanmak uzere yazdirilir. */
@Service
@RequiredArgsConstructor
public class RenderBlankTemplatePdfUseCase {

    private final GetFormTemplateUseCase getFormTemplateUseCase;
    private final ConsentFormPdfPort pdfPort;

    public RenderedConsentPdf execute(UUID templateId) {
        FormTemplateDetail t = getFormTemplateUseCase.execute(templateId);
        byte[] pdf = pdfPort.render(new ConsentFormPdfData(
            t.clinicName(), t.title(), t.content(), Map.of(), null, null, null, null, false, null, true));
        return new RenderedConsentPdf(pdf, t.title(), LocalDate.now(ZoneId.of("Europe/Istanbul")));
    }
}
