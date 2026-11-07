package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.SignedFormDetail;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormDigest;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import com.vetos.modules.consentform.domain.SignedFormValues;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.storage.StoredFileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSignedFormUseCase {

    private final SignedFormRepository signedFormRepository;
    private final StaffUserLookupPort staffUserLookupPort;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public SignedFormDetail execute(UUID id) {
        SignedForm f = signedFormRepository.findById(id).orElseThrow(() -> new SignedFormNotFoundException(id));
        String signedByName = staffUserLookupPort.findSummaryById(f.getSignedBy()).fullName();
        boolean ok = integrityOk(f);
        FormTemplateContent content = null;
        Map<String, Object> values = null;
        try {
            content = FormTemplateContentCodec.fromJson(f.getTemplateSnapshot());
            values = SignedFormValues.fromJson(f.getValuesJson());
        } catch (RuntimeException e) {
            ok = false; // kurcalanmis/bozuk JSON: 500 yerine integrityOk=false
        }
        return new SignedFormDetail(
            f.getId(), f.getTemplateTitle(), content, values,
            new SignerInput(f.getSignerType(), f.getSignerName(), f.getSignerNationalId(), f.getSignerPhone()),
            f.getSignedAt(), signedByName, f.getPatientId(), f.getOwnerId(), f.getEncounterId(),
            f.getStatus(), f.getVoidReason(), f.getVoidedAt(), ok);
    }

    private boolean integrityOk(SignedForm f) {
        try {
            byte[] png = fileStoragePort.retrieve(f.getSignatureStorageRef());
            String signerJson = SignedFormValues.signerJson(
                f.getSignerType(), f.getSignerName(), f.getSignerNationalId(), f.getSignerPhone());
            String metadataJson = SignedFormValues.metadataJson(f.getTemplateId(), f.getPatientId(), f.getOwnerId(),
                f.getEncounterId(), f.getSignedAt(), f.getSignedBy(), f.getClientIp(), f.getUserAgent());
            return SignedFormDigest.compute(f.getTemplateSnapshot(), f.getValuesJson(), signerJson, metadataJson, png)
                .equals(f.getContentDigest());
        } catch (StoredFileNotFoundException e) {
            return false;
        }
    }
}
