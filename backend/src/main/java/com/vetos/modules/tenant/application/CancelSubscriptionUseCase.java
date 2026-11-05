package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.SubscriptionOverview;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.SubscriptionRepository;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotCancellableConflictException;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Klinigin kendi aboneligini self-servis iptal etmesi (Ayarlar > Uyelik
 * panelinden). Sadece ACTIVE abonelikler iptal edilebilir -- TRIAL'da zaten
 * otomatik faturalama yok, PAST_DUE/SUSPENDED kiraci giris yapamadigi icin
 * bu ekrana erisemez. Iptal aninda erisim KESILMEZ: subscription CANCELED'a
 * geçer ama renewsAt (zaten odenmis donem sonu) degismez; o tarih gecince
 * SuspendExpiredCancellationsUseCase (platformadmin, gunluk scheduler)
 * kiraciyi askiya alir. Bkz. ReactivateSubscriptionUseCase (iptali geri alma).
 */
@Service
@RequiredArgsConstructor
public class CancelSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final AuditPort auditPort;

    @Transactional
    public SubscriptionOverview execute(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        if (subscription.getBillingStatus() != BillingStatus.ACTIVE) {
            throw new SubscriptionNotCancellableConflictException(subscription.getBillingStatus());
        }

        subscription.cancel();
        subscriptionRepository.save(subscription);

        auditPort.record(AuditRecord.of(AuditAction.SUBSCRIPTION_CANCELLED, "SUBSCRIPTION", subscription.getId(),
            AuditLabels.shortId(subscription.getId()), "erisim " + subscription.getRenewsAt() + " tarihine kadar surer"));

        return new SubscriptionOverview(
            subscription.getPlanCode(), subscription.getStartedAt(), subscription.getRenewsAt(), subscription.getBillingStatus()
        );
    }
}
