package com.vetos.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dis servis cagrilarinin sonucunu bellekte (son 30 dk) tutar; hem vetly.integration.calls
 * sayacini hem de "external" health grubunu besler (spec 2026-10-14 S4.1-4.3).
 * Pasif kontrol: dis servise test istegi atilmaz, sadece gercek cagrilarin sonucuna bakilir.
 * Yeniden baslatmada bellek sifirlanir -- deneme yoksa UP sayilir (bilincli).
 */
@Slf4j
public class IntegrationOutcomes {

    public static final String EFATURA = "efatura";
    public static final String SMS = "sms";
    public static final String WHATSAPP = "whatsapp";
    public static final String AI = "ai";
    public static final String PAYMENT = "payment";
    public static final String TARBIL = "tarbil";

    static final Duration WINDOW = Duration.ofMinutes(30);
    static final int MIN_ATTEMPTS = 3;
    static final int MAX_ENTRIES = 1000;

    private record Attempt(Instant at, boolean success) {
    }

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<String, Deque<Attempt>> attempts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> downState = new ConcurrentHashMap<>();

    public IntegrationOutcomes(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * "Servis erisilemiyor" sayilan HTTP durumlari: 5xx, kimlik/yetki (401, 403), bakiye (402),
     * kota (429). Diger 4xx (dogrulama hatasi vb.) is hatasidir -- servis ayakta.
     */
    public static boolean isOutageStatus(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 401 || httpStatus == 402 || httpStatus == 403 || httpStatus == 429;
    }

    public void record(String integration, boolean success) {
        registry.counter("vetly.integration.calls",
            "integration", integration, "outcome", success ? "success" : "failure").increment();
        Deque<Attempt> deque = attempts.computeIfAbsent(integration, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Attempt(clock.instant(), success));
            while (deque.size() > MAX_ENTRIES) {
                deque.removeFirst();
            }
            pruneOld(deque);
        }
    }

    /** AI cagrisini sarar: sureyi vetly.ai.duration'a, sonucu "ai" entegrasyonuna yazar; istisnayi aynen firlatir. */
    public <T> T recordAiCall(String task, String provider, Callable<T> call) throws Exception {
        Timer.Sample sample = Timer.start(registry);
        boolean success = false;
        try {
            T result = call.call();
            success = true;
            return result;
        } finally {
            sample.stop(Timer.builder("vetly.ai.duration").tag("task", task).tag("provider", provider).register(registry));
            record(AI, success);
        }
    }

    public Health health(String integration) {
        int total = 0;
        int successes = 0;
        Instant lastFailure = null;
        Deque<Attempt> deque = attempts.get(integration);
        if (deque != null) {
            synchronized (deque) {
                pruneOld(deque);
                total = deque.size();
                successes = (int) deque.stream().filter(Attempt::success).count();
                lastFailure = deque.stream().filter(a -> !a.success()).map(Attempt::at)
                    .reduce((first, second) -> second).orElse(null);
            }
        }
        boolean down = total >= MIN_ATTEMPTS && successes == 0;
        Boolean wasDown = downState.put(integration, down);
        if (down && !Boolean.TRUE.equals(wasDown)) {
            log.warn("Entegrasyon DOWN: {} -- son {} dakikada {} denemenin hicbiri basarili degil, son hata: {}",
                integration, WINDOW.toMinutes(), total, lastFailure);
        } else if (!down && Boolean.TRUE.equals(wasDown)) {
            log.info("Entegrasyon duzeldi: {}", integration);
        }
        return (down ? Health.down() : Health.up())
            .withDetail("attempts", total)
            .withDetail("successes", successes)
            .build();
    }

    private void pruneOld(Deque<Attempt> deque) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().at().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
