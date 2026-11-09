package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.InvalidSignedFormValuesException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrivacyNoticeUseCase {
    private final PrivacyNoticeRepository repository;
    private final OwnerLookupPort owners;
    private final com.vetos.modules.tenant.domain.TenantLookupPort tenants;
    @Transactional(readOnly=true)
    public PrivacyNotice current() { return repository.find(TenantContext.current()).orElseGet(() -> CommonPrivacyNotice.forClinic(
        tenants.findTenantName(TenantContext.current()).orElse("Veteriner Kliniği"))); }
    @Transactional
    public PrivacyNotice save(String text, long expectedVersion, UUID staffId) {
        if (text == null || text.isBlank() || text.length() > 20000 || expectedVersion < 0)
            throw new InvalidSignedFormValuesException("Aydınlatma metni 1–20000 karakter olmalıdır");
        var stored = repository.find(TenantContext.current());
        long storedVersion = stored.map(PrivacyNotice::version).orElse(1L);
        if (expectedVersion != storedVersion) throw new InvalidSignedFormValuesException("Aydınlatma metni değişti; güncel sürümü yükleyin");
        if (!repository.save(TenantContext.current(), text.strip(), stored.isEmpty() ? 0 : expectedVersion, staffId))
            throw new InvalidSignedFormValuesException("Aydınlatma metni değişti; güncel sürümü yükleyin");
        return current();
    }
    @Transactional
    public void recordDelivery(UUID ownerId, String hash, UUID staffId, String ip) {
        owners.findSummaryById(ownerId); // Başka kliniğin sahibi için kayıt oluşturulamaz.
        var notice = requireCurrent(current(),hash);
        repository.recordDelivery(TenantContext.current(),ownerId,notice,staffId,ip);
    }
    @Transactional(readOnly=true)
    public List<PrivacyNoticeRepository.Delivery> deliveries(UUID ownerId) {
        owners.findSummaryById(ownerId);
        return repository.deliveries(TenantContext.current(),ownerId);
    }
    public static PrivacyNotice requireCurrent(PrivacyNotice notice, String hash) {
        if (notice == null) throw new InvalidSignedFormValuesException("Klinik aydınlatma metni henüz tanımlanmamış; yöneticinizle görüşün");
        if (!notice.hash().equals(hash)) throw new InvalidSignedFormValuesException("Aydınlatma metni değişti; güncel metni açıp yeniden imzalayın");
        return notice;
    }
}
