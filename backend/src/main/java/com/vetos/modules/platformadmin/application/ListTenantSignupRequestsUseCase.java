package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Platform admin "Potansiyel Musteriler" (lead) paneli -- vetly.com'da
 * odeme baslatip tenant'i henuz olusmamis (PENDING) ya da basariyla
 * tamamlanmis (COMPLETED) butun kayitlari gosterir. PENDING kayitlar,
 * odemesi yarida kalmis/basarisiz olmus potansiyel musterilerdir -- admin
 * bunlari telefon/e-posta ile takip edebilir (bkz. HandleSignupPaymentCallbackUseCase).
 */
@Service
@RequiredArgsConstructor
public class ListTenantSignupRequestsUseCase {

    private final TenantSignupRequestRepository tenantSignupRequestRepository;

    @Transactional(readOnly = true)
    public List<TenantSignupRequest> execute() {
        return tenantSignupRequestRepository.findAllByOrderByCreatedAtDesc();
    }
}
