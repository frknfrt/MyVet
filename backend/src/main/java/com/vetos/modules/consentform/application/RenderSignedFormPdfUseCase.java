package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.RenderedConsentPdf;
import com.vetos.modules.consentform.application.dto.SignedFormDetail;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.modules.consentform.domain.ConsentFormPdfData;
import com.vetos.modules.consentform.domain.ConsentFormPdfPort;
import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import com.vetos.modules.consentform.domain.SignedFormStatus;
import com.vetos.modules.consentform.domain.SignerType;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.storage.StoredFileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RenderSignedFormPdfUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final GetSignedFormUseCase getSignedFormUseCase;
    private final GetSignatureImageUseCase getSignatureImageUseCase;
    private final SignedFormRepository signedFormRepository;
    private final TenantLookupPort tenantLookupPort;
    private final ConsentFormPdfPort pdfPort;

    public RenderedConsentPdf execute(UUID id) {
        SignedFormDetail d = getSignedFormUseCase.execute(id);
        SignedForm f = signedFormRepository.findById(id).orElseThrow(() -> new SignedFormNotFoundException(id));
        String clinicName = tenantLookupPort.findTenantName(f.getTenantId()).orElse("Veteriner Kliniği");
        byte[] png = null;
        try {
            png = getSignatureImageUseCase.execute(id);
        } catch (StoredFileNotFoundException e) {
            // imza dosyasi yok: kutu bos basilir, integrityOk zaten false
        }
        String digest = f.getContentDigest();
        String digestShort = digest == null ? null : digest.substring(0, Math.min(16, digest.length()));
        return new RenderedConsentPdf(pdfPort.render(new ConsentFormPdfData(
            clinicName, d.templateTitle(), d.content(), d.values(), signerLine(d.signer()), d.signedAt(),
            png, digestShort, d.status() == SignedFormStatus.VOIDED, d.voidReason(), d.integrityOk())),
            d.templateTitle(), d.signedAt().atZone(ISTANBUL).toLocalDate());
    }

    static String signerLine(SignerInput s) {
        if (s.type() == SignerType.OWNER) {
            return "İmzalayan: " + s.name() + " (hasta sahibi)";
        }
        String tc = s.nationalId() == null || s.nationalId().isBlank() ? "" : ", TC: " + s.nationalId();
        return "İmzalayan: " + s.name() + " (hasta yakını/diğer)" + tc;
    }
}
