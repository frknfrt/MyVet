package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.CreateSignedFormCommand;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.FormTemplateArchivedException;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import com.vetos.modules.consentform.domain.exception.InvalidSignatureException;
import com.vetos.modules.consentform.domain.exception.InvalidSignedFormValuesException;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.EncounterSummary;
import com.vetos.modules.consentform.domain.exception.InvalidEncounterException;
import com.vetos.platform.exception.DomainException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CreateSignedFormUseCase {

    static final int MAX_SIGNATURE_BYTES = 200 * 1024;
    private static final int MAX_SIGNATURE_BASE64_CHARS = ((MAX_SIGNATURE_BYTES + 2) / 3) * 4;
    private static final int MAX_USER_AGENT = 500;
    private static final Pattern NATIONAL_ID = Pattern.compile("\\d{11}");

    private final SignedFormSubmissionRepository submissions;
    private final PrivacyNoticeUseCase privacyNotices;
    private final com.vetos.modules.patient.domain.OwnerCommunicationPreferencesPort communicationPreferences;
    private final FormTemplateRepository templateRepository;
    private final SignedFormRepository signedFormRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final FileStoragePort fileStoragePort;
    private final EncounterLookupPort encounterLookupPort;

    @Transactional
    public UUID execute(UUID staffId, CreateSignedFormCommand cmd) {
        if (cmd.requestId() == null) throw new InvalidSignedFormValuesException("İşlem anahtarı zorunludur");
        if (cmd.templateContentHash() == null || cmd.templateContentHash().isBlank()) {
            throw new com.vetos.modules.consentform.domain.exception.FormTemplateChangedConflictException();
        }
        byte[] png = decodeSignature(cmd.signaturePngBase64());
        String valuesHashInput = SignedFormValues.toJson(
            new java.util.TreeMap<>(cmd.values() == null ? Map.of() : cmd.values()));
        String requestHash = SignedFormDigest.compute(cmd.templateContentHash(), valuesHashInput,
            SignedFormValues.toJson(cmd.signer()),
            SignedFormValues.toJson(java.util.Arrays.asList(staffId, cmd.templateId(), cmd.patientId(), cmd.encounterId(), cmd.privacyNoticeAcknowledged(), cmd.privacyNoticeHash(), cmd.smsNotification(), cmd.whatsappNotification(), cmd.emailNotification())), png);
        UUID tenantId = TenantContext.current();
        var previous = submissions.claim(tenantId, cmd.requestId(), requestHash);
        if (previous.isPresent()) return previous.get();

        FormTemplate template = templateRepository.findById(cmd.templateId())
            .orElseThrow(() -> new FormTemplateNotFoundException(cmd.templateId()));
        if (template.getStatus() != FormTemplateStatus.ACTIVE) {
            throw new FormTemplateArchivedException("Arşivlenmiş şablonla form imzalanamaz");
        }
        if (!template.contentHash().equals(cmd.templateContentHash())) {
            throw new com.vetos.modules.consentform.domain.exception.FormTemplateChangedConflictException();
        }
        PatientSummary patient = patientLookupPort.findSummaryById(cmd.patientId());

        validateEncounter(cmd.encounterId(), patient.id());

        FormTemplateContent content = FormTemplateContentCodec.fromJson(template.getContent());
        FormTemplateContentValidator.validate(content);
        Map<String, Object> values = SignedFormValues.normalize(content, cmd.values());
        SignerInput signer = resolveSigner(cmd.signer(), patient);
        String valuesJson = SignedFormValues.toJson(values);
        PrivacyNoticeAcknowledgement acknowledgement = null;
        if (content.privacyOptionsEnabled()) {
            if (!cmd.privacyNoticeAcknowledged()) throw new InvalidSignedFormValuesException("İmzalayanın aydınlatma beyanı zorunludur");
            PrivacyNotice notice = PrivacyNoticeUseCase.requireCurrent(privacyNotices.current(), cmd.privacyNoticeHash());
            acknowledgement = PrivacyNoticeAcknowledgement.of(notice, cmd.smsNotification(), cmd.whatsappNotification(), cmd.emailNotification());
        } else if (cmd.privacyNoticeAcknowledged() || cmd.privacyNoticeHash() != null || cmd.smsNotification() || cmd.whatsappNotification() || cmd.emailNotification()) {
            throw new InvalidSignedFormValuesException("Bu şablonda KVKK ve iletişim tercihleri bölümü etkin değil");
        }
        String snapshot = FormTemplateContentCodec.toJson(new FormTemplateContent(content.title(), content.sections(),
            acknowledgement, content.privacyOptionsEnabled()));
        String signerJson = SignedFormValues.signerJson(signer.type(), signer.name(), signer.nationalId(), signer.phone());
        String ref = fileStoragePort.store(png, "image/png");
        String ua = cmd.userAgent() == null ? null
            : cmd.userAgent().substring(0, Math.min(cmd.userAgent().length(), MAX_USER_AGENT));
        Instant signedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String metadataJson = SignedFormValues.metadataJson(template.getId(), patient.id(), patient.ownerId(),
            cmd.encounterId(), signedAt, staffId, cmd.clientIp(), ua);
        String digest = SignedFormDigest.compute(snapshot, valuesJson, signerJson, metadataJson, png);

        SignedForm form = SignedForm.create(
            TenantContext.current(), template.getId(), template.getTitle(), snapshot, valuesJson, ref,
            patient.id(), patient.ownerId(), cmd.encounterId(),
            signer.type(), signer.name(), signer.nationalId(), signer.phone(),
            staffId, signedAt, cmd.clientIp(), ua, digest);
        UUID id = signedFormRepository.save(form).getId();
        if (acknowledgement != null && signer.type() == SignerType.OWNER) {
            communicationPreferences.apply(patient.ownerId(), acknowledgement.sms(), acknowledgement.whatsapp(), acknowledgement.email());
        }
        submissions.complete(tenantId, cmd.requestId(), id);
        return id;
    }

    /** Muayene yoksa / baska kiracinin ise / baska hastaya aitse hep ayni 422 (404 ayrimi sizdirilmaz). */
    private void validateEncounter(UUID encounterId, UUID patientId) {
        if (encounterId == null) return;
        EncounterSummary e;
        try {
            e = encounterLookupPort.findSummaryById(encounterId);
        } catch (DomainException ex) {
            throw new InvalidEncounterException();
        }
        if (!patientId.equals(e.patientId())) throw new InvalidEncounterException();
    }

    private SignerInput resolveSigner(SignerInput in, PatientSummary patient) {
        if (in == null || in.type() == null) {
            throw new InvalidSignedFormValuesException("İmzalayan bilgisi zorunludur");
        }
        String nationalId = blankToNull(in.nationalId());
        String phone = blankToNull(in.phone());
        if (nationalId != null && !NATIONAL_ID.matcher(nationalId).matches()) {
            throw new InvalidSignedFormValuesException("T.C. kimlik numarası 11 haneli rakam olmalıdır");
        }
        if (in.type() == SignerType.OWNER) {
            String ownerName = ownerLookupPort.findSummaryById(patient.ownerId()).fullName();
            return new SignerInput(SignerType.OWNER, ownerName, nationalId, phone);
        }
        String name = blankToNull(in.name());
        if (name == null) throw new InvalidSignedFormValuesException("İmzalayan adı zorunludur");
        return new SignerInput(SignerType.OTHER, name, nationalId, phone);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static byte[] decodeSignature(String base64) {
        if (base64 == null || base64.isBlank()) throw new InvalidSignatureException("İmza zorunludur");
        String encoded = base64.strip();
        if (encoded.length() > MAX_SIGNATURE_BASE64_CHARS) {
            throw new InvalidSignatureException("İmza çok büyük (en çok 200 KB)");
        }
        int padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
        int decodedLength = encoded.length() * 3 / 4 - padding;
        if (decodedLength > MAX_SIGNATURE_BYTES) {
            throw new InvalidSignatureException("İmza çok büyük (en çok 200 KB)");
        }
        byte[] png;
        try {
            png = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new InvalidSignatureException("İmza çözülemedi");
        }
        if (png.length > MAX_SIGNATURE_BYTES) throw new InvalidSignatureException("İmza çok büyük (en çok 200 KB)");
        if (!FormImageFormat.detectContentType(png).filter("image/png"::equals).isPresent()) {
            throw new InvalidSignatureException("İmza PNG biçiminde olmalıdır");
        }
        return png;
    }
}
