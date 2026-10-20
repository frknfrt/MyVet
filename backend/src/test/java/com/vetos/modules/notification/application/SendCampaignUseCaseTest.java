package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.CampaignRecipient;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.ratelimit.RateLimitExceededException;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SendCampaignUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final TenantRateLimiter limiter = mock(TenantRateLimiter.class);
    private final UsageQuotaPort quota = mock(UsageQuotaPort.class);
    private final QueueNotificationUseCase queue = mock(QueueNotificationUseCase.class);
    private final SendCampaignUseCase useCase = new SendCampaignUseCase(queue, limiter, quota);

    private final List<CampaignRecipient> threeEligible = List.of(eligible(1), eligible(2), eligible(3));

    private static CampaignRecipient eligible(int i) {
        return new CampaignRecipient(UUID.randomUUID(), "0555000000" + i, true, true, Map.of("ad", "Ali" + i), "Ali" + i);
    }

    private static CampaignRecipient noPhone() {
        return new CampaignRecipient(UUID.randomUUID(), " ", true, true, Map.of(), "Telefonsuz");
    }

    private static CampaignRecipient noConsent() {
        return new CampaignRecipient(UUID.randomUUID(), "05550000099", false, false, Map.of(), "Izinsiz");
    }

    @Test
    void should_consumeAndReserve_onlyForEligibleRecipients() {
        var recipients = List.of(eligible(1), eligible(2), eligible(3), noPhone(), noConsent());

        var summary = useCase.execute(TENANT, NotificationChannel.SMS, "Merhaba {ad}", recipients);

        InOrder order = inOrder(limiter, quota, queue);
        order.verify(limiter).consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        order.verify(quota).reserve(TENANT, UsageKind.MESSAGE, 3);
        order.verify(queue, times(3)).execute(eq(TENANT), any(), isNull(), eq(NotificationChannel.SMS),
            eq(NotificationType.CAMPAIGN_MESSAGE), any(), any(), isNull(), any());
        assertThat(summary.queued()).isEqualTo(3);
    }

    @Test
    void should_touchNothing_when_noEligibleRecipients() {
        useCase.execute(TENANT, NotificationChannel.SMS, "x", List.of(noPhone()));
        verifyNoInteractions(limiter, quota, queue);
    }

    @Test
    void should_notReserveQuota_when_hourlyLimitRejects() {
        doThrow(new RateLimitExceededException(120)).when(limiter).consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(quota, queue);
    }

    @Test
    void should_refundHourlyTokens_when_quotaRejects() {
        doThrow(new MonthlyQuotaExceededException(UsageKind.MESSAGE, 900, 1000, 3)).when(quota).reserve(TENANT, UsageKind.MESSAGE, 3);
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .isInstanceOf(MonthlyQuotaExceededException.class);
        verify(limiter).refund(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        verifyNoInteractions(queue);
    }

    @Test
    void should_releaseQuota_when_queueingFails() {
        when(queue.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID())
            .thenThrow(new IllegalStateException("db"));
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .hasMessage("db");
        verify(quota).release(TENANT, UsageKind.MESSAGE, 3);
        verify(limiter, never()).refund(any(), any(), anyLong());
    }

    @Test
    void should_keepSkipCountsAsBefore() {
        var summary = useCase.execute(TENANT, NotificationChannel.SMS, "x", List.of(eligible(1), noPhone(), noConsent()));
        assertThat(summary.queued()).isEqualTo(1);
        assertThat(summary.skippedNoPhone()).isEqualTo(1);
        assertThat(summary.skippedNoConsent()).isEqualTo(1);
    }
}
