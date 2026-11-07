package com.vetos.modules.dataimport.application;

import com.vetos.platform.tenancy.TenantContext;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aktarimin kisa, bagimsiz (REQUIRES_NEW) transaction'lari.
 *
 * Hibernate kiraciyi Session acilirken BIR KEZ okur; bu yuzden TenantContext transaction BASLAMADAN once kurulur
 * ve finally'de cagirandan onceki deger aynen geri yuklenir (bkz. TenantScopedTestSupport yorumu).
 * ImportBatch'te @TenantId yok: parti durumu kiracisiz (root) transaction'da guncellenir.
 */
@Component
class ImportTransactions {

    private final TransactionTemplate template;

    ImportTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    <T> T asTenant(UUID tenantId, Supplier<T> work) {
        UUID previous = TenantContext.currentOrNull();
        TenantContext.set(tenantId);
        try {
            return template.execute(status -> work.get());
        } finally {
            restore(previous);
        }
    }

    <T> T asRoot(Supplier<T> work) {
        UUID previous = TenantContext.currentOrNull();
        TenantContext.clear();
        try {
            return template.execute(status -> work.get());
        } finally {
            restore(previous);
        }
    }

    void asRootVoid(Runnable work) {
        asRoot(() -> {
            work.run();
            return null;
        });
    }

    private static void restore(UUID previous) {
        if (previous != null) {
            TenantContext.set(previous);
        } else {
            TenantContext.clear();
        }
    }
}
