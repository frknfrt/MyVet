package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialEmailPort;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.EndingTrial;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemindEndingTrialsUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final String UPGRADE_URL = "http://localhost:5173/ayarlar/abonelik";

    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final TrialSmsPort smsPort = mock(TrialSmsPort.class);
    private final TrialEmailPort emailPort = mock(TrialEmailPort.class);

    private final RemindEndingTrialsUseCase useCase =
        new RemindEndingTrialsUseCase(tenantAdminPort, smsPort, emailPort, "http://localhost:5173");

    private UUID tenant(String name, String email, String phone) {
        UUID tenantId = UUID.randomUUID();
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(new TenantAdminOverview(
            tenantId, name, "-", TenantStatus.TRIAL, Instant.now(), "TRIAL", BillingStatus.TRIAL,
            TODAY.minusDays(5), TODAY.plusDays(2), 1, 1, null, null
        ));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.ofNullable(email));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.ofNullable(phone));
        return tenantId;
    }

    @Test
    void should_sendEndingSoon_when_trialEndsInTwoDays() {
        UUID tenantId = tenant("Pati Klinik", "ayse@ornek.com", "+905321112233");
        when(tenantAdminPort.listTrialsEndingOn(TODAY.plusDays(2))).thenReturn(List.of(new EndingTrial(tenantId, TODAY.plusDays(2))));

        int sent = useCase.execute(TODAY);

        assertThat(sent).isEqualTo(1);
        verify(smsPort).sendTrialEndingSoon("+905321112233", "Pati Klinik", TODAY.plusDays(2));
        verify(emailPort).sendTrialEndingSoon("ayse@ornek.com", "Pati Klinik", TODAY.plusDays(2), UPGRADE_URL);
    }

    @Test
    void should_sendLastDay_when_trialEndsToday() {
        UUID tenantId = tenant("Pati Klinik", "ayse@ornek.com", "+905321112233");
        when(tenantAdminPort.listTrialsEndingOn(TODAY)).thenReturn(List.of(new EndingTrial(tenantId, TODAY)));

        int sent = useCase.execute(TODAY);

        assertThat(sent).isEqualTo(1);
        verify(smsPort).sendTrialLastDay("+905321112233", "Pati Klinik");
        verify(emailPort).sendTrialLastDay("ayse@ornek.com", "Pati Klinik", UPGRADE_URL);
    }

    @Test
    void should_sendNothing_when_noTrialEndsTodayOrInTwoDays() {
        // listTrialsEndingOn yalnizca bugun ve bugun+2 icin sorulur; yarin ve 3 gun sonrasi sorulmaz.
        int sent = useCase.execute(TODAY);

        assertThat(sent).isZero();
        verify(tenantAdminPort).listTrialsEndingOn(TODAY);
        verify(tenantAdminPort).listTrialsEndingOn(TODAY.plusDays(2));
        verifyNoInteractions(smsPort, emailPort);
    }

    @Test
    void should_continueWithNextTenant_when_oneNotificationFails() {
        UUID failing = tenant("Bozuk Klinik", "bozuk@ornek.com", "+905320000000");
        UUID healthy = tenant("Pati Klinik", "ayse@ornek.com", "+905321112233");
        when(tenantAdminPort.listTrialsEndingOn(TODAY)).thenReturn(List.of(
            new EndingTrial(failing, TODAY), new EndingTrial(healthy, TODAY)));
        doThrow(new RuntimeException("sms hatasi")).when(smsPort).sendTrialLastDay("+905320000000", "Bozuk Klinik");

        int sent = useCase.execute(TODAY);

        assertThat(sent).isEqualTo(1);
        verify(smsPort).sendTrialLastDay("+905321112233", "Pati Klinik");
        verify(emailPort).sendTrialLastDay("ayse@ornek.com", "Pati Klinik", UPGRADE_URL);
    }

    @Test
    void should_skipSms_when_billingContactHasNoPhone() {
        UUID tenantId = tenant("Pati Klinik", "ayse@ornek.com", null);
        when(tenantAdminPort.listTrialsEndingOn(TODAY)).thenReturn(List.of(new EndingTrial(tenantId, TODAY)));

        useCase.execute(TODAY);

        verifyNoInteractions(smsPort);
        verify(emailPort).sendTrialLastDay(anyString(), anyString(), anyString());
    }

    @Test
    void should_skipEmail_when_billingContactHasNoEmail() {
        UUID tenantId = tenant("Pati Klinik", null, "+905321112233");
        when(tenantAdminPort.listTrialsEndingOn(TODAY.plusDays(2))).thenReturn(List.of(new EndingTrial(tenantId, TODAY.plusDays(2))));

        useCase.execute(TODAY);

        verify(smsPort).sendTrialEndingSoon(anyString(), anyString(), any());
        verifyNoInteractions(emailPort);
    }
}
