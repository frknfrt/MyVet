package com.vetos.platform.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_propagateSubmittersMdc_toWorkerThread() throws InterruptedException {
        MDC.put("requestId", "req-A");
        MDC.put("tenantId", "tenant-1");
        AtomicReference<String> seenRequestId = new AtomicReference<>();
        AtomicReference<String> seenTenantId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            seenRequestId.set(MDC.get("requestId"));
            seenTenantId.set(MDC.get("tenantId"));
        });

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seenRequestId.get()).isEqualTo("req-A");
        assertThat(seenTenantId.get()).isEqualTo("tenant-1");
    }

    @Test
    void should_notLeakStaleMdc_when_submitterHadNone() throws InterruptedException {
        AtomicReference<String> seenDuring = new AtomicReference<>("baslangic");
        AtomicReference<String> seenAfter = new AtomicReference<>("baslangic");
        // Gonderen (test thread'i) MDC'siz -- decorate BURADA, worker'dan once cagrilmali.
        Runnable decorated = decorator.decorate(() -> seenDuring.set(MDC.get("requestId")));

        Thread pooledWorker = new Thread(() -> {
            MDC.put("requestId", "onceki-gorevden-kalan");
            decorated.run();
            seenAfter.set(MDC.get("requestId"));
        });
        pooledWorker.start();
        pooledWorker.join();

        assertThat(seenDuring.get()).isNull();
        assertThat(seenAfter.get()).isEqualTo("onceki-gorevden-kalan");
    }

    @Test
    void should_restoreCallerMdc_when_runOnCallerThread() {
        MDC.put("requestId", "req-A");
        Runnable decorated = decorator.decorate(() -> MDC.put("requestId", "gorev-icinde-degisti"));

        MDC.put("requestId", "req-B");
        decorated.run();

        assertThat(MDC.get("requestId")).isEqualTo("req-B");
    }
}
