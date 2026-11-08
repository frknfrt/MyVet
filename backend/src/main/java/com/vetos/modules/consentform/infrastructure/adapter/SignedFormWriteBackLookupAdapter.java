package com.vetos.modules.consentform.infrastructure.adapter;

import com.vetos.modules.consentform.application.GetSignedFormUseCase;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.SignedFormStatus;
import com.vetos.modules.consentform.domain.SignerType;
import com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort;
import com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SignedFormWriteBackLookupAdapter implements SignedFormWriteBackLookupPort {
    private final GetSignedFormUseCase getSignedForm;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Set<String>> allowedValues(UUID signedFormId, UUID patientId, UUID ownerId) {
        if (signedFormId == null) throw new InvalidSourceException();
        var form = getSignedForm.execute(signedFormId);
        if (!patientId.equals(form.patientId()) || !ownerId.equals(form.ownerId())
            || form.status() != SignedFormStatus.VALID || !form.integrityOk()
            || form.content() == null || form.values() == null || form.signer() == null) {
            throw new InvalidSourceException();
        }
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        for (var section : form.content().sections()) {
            for (var item : section.items()) {
                String binding = item.binding();
                if (binding == null || item.type() == FormItemType.CHOICE
                    || item.type() == FormItemType.SIGNATURE || item.type() == FormItemType.TEXT_BLOCK) continue;
                if (binding.startsWith("owner.") && form.signer().type() != SignerType.OWNER) continue;
                Object value = form.values().get(item.code());
                if (value instanceof String text && !text.isBlank()) {
                    allowed.computeIfAbsent(binding, key -> new HashSet<>()).add(text.trim());
                }
            }
        }
        allowed.replaceAll((key, values) -> Set.copyOf(values));
        return Map.copyOf(allowed);
    }
}
