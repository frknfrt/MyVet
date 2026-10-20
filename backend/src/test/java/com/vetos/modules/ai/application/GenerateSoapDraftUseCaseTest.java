package com.vetos.modules.ai.application;

import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenerateSoapDraftUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    private final SoapGenerationPort port = mock(SoapGenerationPort.class);
    private final UsageQuotaPort quota = mock(UsageQuotaPort.class);
    private final GenerateSoapDraftUseCase useCase = new GenerateSoapDraftUseCase(port, quota, new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()), new TenantRateLimiter(new RateLimitProperties(600, 10, 2000))));

    @Test
    void should_reserveBeforeCalling_and_keep_when_modelConnected() {
        SoapDraft draft = new SoapDraft("s", "o", "a", "p", true);
        when(port.generate(any(AudioTranscript.class))).thenReturn(draft);

        assertThat(useCase.execute(TENANT, "metin")).isEqualTo(draft);

        InOrder order = inOrder(quota, port);
        order.verify(quota).reserve(TENANT, UsageKind.AI, 1);
        order.verify(port).generate(any());
        verify(quota, never()).release(any(), any(), anyInt());
    }

    @Test
    void should_release_when_modelNotConnected() {
        SoapDraft fallback = new SoapDraft("metin", "", "", "", false);
        when(port.generate(any())).thenReturn(fallback);

        assertThat(useCase.execute(TENANT, "metin")).isEqualTo(fallback);

        verify(quota).release(TENANT, UsageKind.AI, 1);
    }

    @Test
    void should_release_and_rethrow_when_modelThrows() {
        when(port.generate(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> useCase.execute(TENANT, "metin")).hasMessage("boom");

        verify(quota).release(TENANT, UsageKind.AI, 1);
    }

    @Test
    void should_notCallModel_when_quotaExhausted() {
        doThrow(new MonthlyQuotaExceededException(UsageKind.AI, 30, 30, 1)).when(quota).reserve(TENANT, UsageKind.AI, 1);

        assertThatThrownBy(() -> useCase.execute(TENANT, "metin")).isInstanceOf(MonthlyQuotaExceededException.class);

        verifyNoInteractions(port);
    }

    @Test
    void should_notReserveQuota_when_bulkheadFull() {
        AiCallGate fullGate = mock(AiCallGate.class);
        when(fullGate.run(any(), any())).thenThrow(new AiBusyServiceUnavailableException());
        GenerateSoapDraftUseCase gated = new GenerateSoapDraftUseCase(port, quota, fullGate);

        assertThatThrownBy(() -> gated.execute(TENANT, "metin")).isInstanceOf(AiBusyServiceUnavailableException.class);

        verifyNoInteractions(quota);
        verifyNoInteractions(port);
    }
}
