package com.vetos;

import com.vetos.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * TenantIsolationTest ve gelecekteki benzer testler icin: her "X kiracisi
 * olarak" islemi kendi TAZE Hibernate Session'inda calistirir (REQUIRES_NEW).
 *
 * NEDEN BOYLE: Hibernate'in CurrentTenantIdentifierResolver'i,
 * SessionFactoryImpl$SessionBuilderImpl constructor'inda -- yani Session
 * acilirken -- BIR KEZ cagrilir ve degeri o Session/transaction boyunca
 * sabitlenir. Bu yuzden test metoduna @Transactional koyup metodun ortasinda
 * TenantContext'i degistirmek Hibernate'in tenant identifier'i YENIDEN
 * okumasini SAGLAMAZ; test yanlislikla yesil (ya da anlamsiz) cikar.
 * Cozum: her farkli kiracidan islem, TenantContext o transaction
 * BASLAMADAN HEMEN ONCE set edilerek, kendi REQUIRES_NEW transaction'inda
 * calisir. Bunun dogal sonucu, test metotlarinin @Transactional
 * auto-rollback'ine GUVENEMEMESIdir -- temizlik elle yapilir
 * (bkz. TenantIsolationTest.purgeTenant).
 *
 * Alternatif olarak @Autowired EntityManagerFactory ile elle
 * createEntityManager/begin/commit yapilabilirdi; TransactionTemplate
 * tercih edildi cunku Spring Data repository'leri (testin dogruladigi
 * gercek uretim yolu) transaction'a boylece dogal olarak katiliyor ve
 * EntityManager yasam dongusunu elle yonetmek gerekmiyor.
 */
abstract class TenantScopedTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Verilen kiraci kimligiyle, TAZE bir Session/transaction icinde calistirir. */
    protected <T> T asTenant(UUID tenantId, Supplier<T> work) {
        TenantContext.set(tenantId);
        try {
            return newTransaction().execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    protected void asTenantVoid(UUID tenantId, Runnable work) {
        asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * TenantContext KURULMADAN, taze bir transaction icinde calistirir --
     * login, /api/v1/public/**, platform admin ve @Scheduled islerin
     * gercek durumunu taklit eder (root Session, _tenantId filtresi kapali).
     */
    protected <T> T inRootSession(Supplier<T> work) {
        TenantContext.clear();
        return newTransaction().execute(status -> work.get());
    }

    protected void inRootSessionVoid(Runnable work) {
        inRootSession(() -> {
            work.run();
            return null;
        });
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
}
