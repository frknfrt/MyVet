package com.vetos.platform.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiraci basina bellek-ici kovalar (tek instance). Kova anahtari havuz + tenantId;
 * 2 saat kullanilmayan kova silinir, en fazla 50.000 kova tutulur -- bellek sinirli.
 */
public class TenantRateLimiter {

    private final Map<TenantPool, Bandwidth> limits = new EnumMap<>(TenantPool.class);
    private final Map<TenantPool, Duration> periods = new EnumMap<>(TenantPool.class);
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(2))
        .maximumSize(50_000)
        .build();

    public TenantRateLimiter(RateLimitProperties properties) {
        define(TenantPool.GENERAL, properties.tenantGeneralPerMinute(), Duration.ofMinutes(1));
        define(TenantPool.AI, properties.tenantAiPerMinute(), Duration.ofMinutes(1));
        define(TenantPool.CAMPAIGN_RECIPIENTS, properties.campaignRecipientsPerHour(), Duration.ofHours(1));
    }

    private void define(TenantPool pool, int capacity, Duration period) {
        limits.put(pool, Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build());
        periods.put(pool, period);
    }

    public ConsumeResult tryConsume(TenantPool pool, UUID tenantId, long tokens) {
        if (tokens <= 0) {
            return ConsumeResult.ok();
        }
        if (tokens > limits.get(pool).getCapacity()) {
            // Kova kapasitesini asan istek hic karsilanamaz; Bucket4j'nin bekleme suresi anlamsiz
            // olur -- havuzun tam periyodu kadar beklemesini soyleriz.
            return new ConsumeResult(false, periods.get(pool).toSeconds());
        }
        ConsumptionProbe probe = bucket(pool, tenantId).tryConsumeAndReturnRemaining(tokens);
        if (probe.isConsumed()) {
            return ConsumeResult.ok();
        }
        return new ConsumeResult(false, toRetrySeconds(probe.getNanosToWaitForRefill()));
    }

    public void consumeOrThrow(TenantPool pool, UUID tenantId, long tokens) {
        ConsumeResult result = tryConsume(pool, tenantId, tokens);
        if (!result.consumed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }

    public void refund(TenantPool pool, UUID tenantId, long tokens) {
        if (tokens <= 0) {
            return;
        }
        Bucket bucket = buckets.getIfPresent(key(pool, tenantId));
        if (bucket != null) {
            bucket.addTokens(tokens);
        }
    }

    public static long toRetrySeconds(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    private Bucket bucket(TenantPool pool, UUID tenantId) {
        return buckets.get(key(pool, tenantId), k -> Bucket.builder().addLimit(limits.get(pool)).build());
    }

    private static String key(TenantPool pool, UUID tenantId) {
        return pool.name() + ":" + tenantId;
    }
}
