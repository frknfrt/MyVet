package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.SubscriptionOverview;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.SubscriptionRepository;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.modules.tenant.domain.exception.SubscriptionReactivationNotAllowedConflictException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * CancelSubscriptionUseCase ile konan iptali geri alir. Sadece odenmis donem
 * (renewsAt) bitmeden once cagirilabilir -- o tarih gecmisse kiraci zaten
 * SuspendExpiredCancellationsUseCase tarafindan askiya alinmistir ve bu
 * ekrana erisemez; yine de yarisda kalan bir durum icin acikca hata verilir.
 */
@Service
@RequiredArgsConstructor
public class ReactivateSubscriptionUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final SubscriptionRepository subscriptionRepository;
    private final AuditPort auditPort;

    @Transactional
    public SubscriptionOverview execute(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        if (subscription.getBillingStatus() != BillingStatus.CANCELED) {
            throw new SubscriptionReactivationNotAllowedConflictException("Bu abonelik iptal edilmemiş, geri alınacak bir şey yok");
        }
        LocalDate today = LocalDate.now(ISTANBUL);
        if (subscription.getRenewsAt() != null && subscription.getRenewsAt().isBefore(today)) {
            throw new SubscriptionReactivationNotAllowedConflictException("İptal dönemi sona erdi, lütfen bizimle iletişime geçin");
        }

        subscription.reactivateCancellation();
        subscriptionRepository.save(subscription);

        auditPort.record(AuditRecord.of(AuditAction.SUBSCRIPTION_CANCELLATION_REVERTED, "SUBSCRIPTION", subscription.getId(),
            AuditLabels.shortId(subscription.getId()), null));

        return new SubscriptionOverview(
            subscription.getPlanCode(), subscription.getStartedAt(), subscription.getRenewsAt(), subscription.getBillingStatus()
        );
    }
}
