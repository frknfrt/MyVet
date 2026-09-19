package com.vetos.platform.concurrency;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Postgres transaction-scoped advisory lock -- mevcut transaction commit/
 * rollback olunca otomatik serbest kalir (oturum/connection havuzunda sizinti
 * riski yok). Coklu instance'ta ayni @Scheduled isin sadece BIR instance
 * tarafindan calistirilmasini garanti etmek icin kullanilir. Cagiran metot
 * @Transactional olmalidir -- kilit o transaction'in omrune baglidir.
 */
@Component
@RequiredArgsConstructor
public class AdvisoryLock {
    private final EntityManager entityManager;

    /** true donerse kilit alindi, is yapilabilir. false donerse baska bir instance zaten calisiyor. */
    public boolean tryAcquire(long key) {
        Object result = entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
            .setParameter("key", key)
            .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
