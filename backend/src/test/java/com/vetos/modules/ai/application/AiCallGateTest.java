package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallGateTest {

    private static final UUID TENANT = UUID.randomUUID();

    // Dakikada 3 AI hakki: iade testinde havuzun tam olarak geri doldugunu gormek icin kucuk.
    private final TenantRateLimiter limiter = new TenantRateLimiter(new RateLimitProperties(600, 3, 2000));

    private AiCallGate gate(int max, Duration wait) {
        return new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(max).maxWaitDuration(wait).build()), limiter);
    }

    @Test
    void should_runWork_andReturnResult() {
        assertThat(gate(4, Duration.ofMillis(100)).run(TENANT, () -> "ok")).isEqualTo("ok");
    }

    @Test
    void should_rejectWithAiBusy_when_fullBeyondWait() throws Exception {
        AiCallGate gate = gate(1, Duration.ofMillis(100));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holder = pool.submit(() -> gate.run(TENANT, () -> {
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "first";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.run(TENANT, () -> "second"))
                .isInstanceOf(AiBusyServiceUnavailableException.class)
                .hasMessage("AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin.");

            release.countDown();
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(gate.run(TENANT, () -> "third")).isEqualTo("third");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_admitWaitingCall_when_slotFreesWithinWait() throws Exception {
        AiCallGate gate = gate(1, Duration.ofSeconds(2));
        CountDownLatch inside = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> gate.run(TENANT, () -> {
                inside.countDown();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "first";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.run(TENANT, () -> "second")).isEqualTo("second");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_refundMinuteAiToken_when_busy() throws Exception {
        AiCallGate gate = gate(1, Duration.ofMillis(100));
        // TenantRateLimitFilter AI ucunda 1 hak dusmus gibi: 3 -> 2
        assertThat(limiter.tryConsume(TenantPool.AI, TENANT, 1).consumed()).isTrue();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> gate.run(UUID.randomUUID(), () -> {
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "holder";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.run(TENANT, () -> "busy")).isInstanceOf(AiBusyServiceUnavailableException.class);

            // Iade edildi: havuz yeniden 3 hakka sahip
            assertThat(limiter.tryConsume(TenantPool.AI, TENANT, 3).consumed()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_notRefund_when_callSucceeds() {
        AiCallGate gate = gate(4, Duration.ofMillis(100));
        assertThat(limiter.tryConsume(TenantPool.AI, TENANT, 1).consumed()).isTrue();
        gate.run(TENANT, () -> "ok");
        assertThat(limiter.tryConsume(TenantPool.AI, TENANT, 3).consumed()).isFalse();
    }
}
