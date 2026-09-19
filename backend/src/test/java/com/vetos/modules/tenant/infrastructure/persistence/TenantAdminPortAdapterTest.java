package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Koprulme kurali (tasarim dokumani S5): platform admin istekleri
 * TenantContext OLMADAN gelir; StaffUser (@TenantId'li) okuyan/yazan
 * cagrilar TenantContext'i elle kurmali, finally'de temizlemeli.
 * Gercek Hibernate yok -- mock repository'ler icinde TenantContext'in
 * degeri yakalanir.
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminPortAdapterTest {

    @Mock private TenantJpaRepository tenantJpaRepository;
    @Mock private SubscriptionJpaRepository subscriptionJpaRepository;
    @Mock private BranchJpaRepository branchJpaRepository;
    @Mock private StaffUserJpaRepository staffUserJpaRepository;
    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DomainEventPublisher eventPublisher;

    private TenantAdminPortAdapter adapter() {
        return new TenantAdminPortAdapter(
            tenantJpaRepository, subscriptionJpaRepository, branchJpaRepository,
            staffUserJpaRepository, staffInviteRepository, passwordEncoder, eventPublisher
        );
    }

    private static Branch branchWithId(UUID tenantId, UUID branchId) {
        Branch branch = Branch.create(tenantId, "Merkez");
        ReflectionTestUtils.setField(branch, "id", branchId);
        return branch;
    }

    private static Tenant tenantWithId(UUID tenantId) {
        Tenant tenant = Tenant.register("Test Klinik", null);
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        return tenant;
    }

    @Test
    void findBillingContactEmail_setsTenantContext_andClearsIt() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        AtomicReference<UUID> seen = new AtomicReference<>();
        when(branchJpaRepository.findByTenantId(tenantId)).thenReturn(List.of(branchWithId(tenantId, branchId)));
        when(staffUserJpaRepository.findByBranchIdInAndRole(anyList(), any(StaffRole.class)))
            .thenAnswer(invocation -> {
                seen.set(TenantContext.current());
                return List.of(StaffUser.register(tenantId, branchId, "Admin", "a@b.c", "hash", StaffRole.ADMIN));
            });

        adapter().findBillingContactEmail(tenantId);

        assertThat(seen.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void createTenant_setsTenantContext_onlyAroundStaffUserWrite() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        List<UUID> contextDuringEmailCheck = new ArrayList<>();
        AtomicReference<UUID> contextDuringSave = new AtomicReference<>();

        when(staffUserJpaRepository.existsByEmail("admin@klinik.test")).thenAnswer(invocation -> {
            // Bu sorgu GLOBAL olmali: staff_users.email tum kiracilarda
            // essiz. Kiraci filtreli bir Session'da calisirsa baska
            // kiracidaki ayni e-posta gorunmez ve unique constraint
            // ihlali 500 olarak patlar. Bu yuzden koprulme buraya DEGIL,
            // sadece StaffUser.register/save cagrisina uygulanir.
            contextDuringEmailCheck.add(TenantContext.currentOrNull());
            return false;
        });
        when(tenantJpaRepository.save(any(Tenant.class))).thenReturn(tenantWithId(tenantId));
        when(branchJpaRepository.save(any(Branch.class))).thenReturn(branchWithId(tenantId, branchId));
        when(subscriptionJpaRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("sifre")).thenReturn("hash");
        when(staffUserJpaRepository.save(any(StaffUser.class))).thenAnswer(invocation -> {
            contextDuringSave.set(TenantContext.current());
            return invocation.getArgument(0);
        });

        adapter().createTenant(
            "Test Klinik", null, "Merkez", "Adres", "Ankara",
            "Admin", "admin@klinik.test", "sifre"
        );

        assertThat(contextDuringEmailCheck).containsExactly((UUID) null);
        assertThat(contextDuringSave.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
    }
}
