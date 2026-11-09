package com.vetos.modules.consentform.application.dto;

import java.util.Map;
import java.util.UUID;

public record CreateSignedFormCommand(
    UUID templateId, UUID patientId, UUID encounterId, Map<String, Object> values,
    SignerInput signer, String signaturePngBase64, String clientIp, String userAgent, String templateContentHash, UUID requestId,
    boolean privacyNoticeAcknowledged, String privacyNoticeHash, boolean smsNotification, boolean whatsappNotification, boolean emailNotification
) {
    public CreateSignedFormCommand(UUID templateId, UUID patientId, UUID encounterId, Map<String,Object> values,
        SignerInput signer, String signaturePngBase64, String clientIp, String userAgent, String templateContentHash, UUID requestId) {
        this(templateId,patientId,encounterId,values,signer,signaturePngBase64,clientIp,userAgent,templateContentHash,requestId,false,null,false,false,false);
    }
    public CreateSignedFormCommand acknowledgingPrivacyNotice(String hash) {
        return new CreateSignedFormCommand(templateId,patientId,encounterId,values,signer,signaturePngBase64,clientIp,userAgent,
            templateContentHash,requestId,true,hash,smsNotification,whatsappNotification,emailNotification);
    }
}
