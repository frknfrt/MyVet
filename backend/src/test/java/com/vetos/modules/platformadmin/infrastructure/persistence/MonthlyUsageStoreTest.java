package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.platform.quota.UsageKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Canli docker-compose Postgres gerekir. Kota dogrulugu SQL'e bagli oldugu icin gercek DB'de. */
@SpringBootTest
class MonthlyUsageStoreTest {

    @Autowired private MonthlyUsageStore store;

    @Test
    void should_incrementUpToQuota_then_rejectWithoutWriting() {
        UUID tenant = UUID.randomUUID();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 2, 3)).isTrue();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 2, 3)).isFalse();
        assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(2);
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 1, 3)).isTrue();
        assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(3);
    }

    @Test
    void should_rejectEverything_when_quotaZero() {
        UUID tenant = UUID.randomUUID();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.MESSAGE, 1, 0)).isFalse();
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }

    @Test
    void should_allowExactlyRemaining_underConcurrency() throws Exception {
        UUID tenant = UUID.randomUUID();
        int quota = 7;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    return store.tryIncrement(tenant, "2026-10", UsageKind.AI, 1, quota);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            long successes = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) successes++;
            }
            assertThat(successes).isEqualTo(quota);
            assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(quota);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_incrementUnconditionally_and_decrementWithFloorZero() {
        UUID tenant = UUID.randomUUID();
        store.increment(tenant, "2026-10", UsageKind.MESSAGE, 5);
        store.increment(tenant, "2026-10", UsageKind.MESSAGE, 4);
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isEqualTo(9);
        store.decrement(tenant, "2026-10", UsageKind.MESSAGE, 20);
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }

    @Test
    void should_keepPeriodsAndKindsSeparate() {
        UUID tenant = UUID.randomUUID();
        store.increment(tenant, "2026-10", UsageKind.AI, 3);
        assertThat(store.used(tenant, "2026-11", UsageKind.AI)).isZero();
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }
}
