package com.vetos.modules.tenant;

import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditEventFilter;
import com.vetos.platform.audit.AuditEventQuery;
import com.vetos.platform.audit.AuditEventView;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review Focus #1: LoginUseCase readOnly transaction'da calisir. Giris olayi recordStandalone (REQUIRES_NEW) ile
 * yazilmazsa Postgres "cannot execute INSERT in a read-only transaction" verir ve giris bozulur. Bu test canli
 * Postgres (docker, 5433) ile gercek giris istegini yapar. audit_events tablosu degistirilemez (trigger) oldugundan
 * olusan olaylar temizlenmez; her calistirmada rastgele kiraci kimligi kullanilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginAuditIntegrationTest {

    private static final String PASSWORD = "Sifre-12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditEventQuery auditEventQuery;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID staffId;
    private String email;

    private <T> T inNewTx(Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> work.get());
    }

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        tenantId = inNewTx(() -> tenantRepository.save(Tenant.register("Giris Denetim " + UUID.randomUUID(), null)).getId());
        UUID branchId = inNewTx(() -> branchRepository.save(Branch.create(tenantId, "Merkez")).getId());
        email = "giris-denetim-" + UUID.randomUUID() + "@test.local";
        TenantContext.set(tenantId);
        try {
            staffId = inNewTx(() -> staffUserRepository.save(StaffUser.register(
                tenantId, branchId, "Dr. Giris", email, passwordEncoder.encode(PASSWORD), StaffRole.VET
            )).getId());
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM staff_users WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    private List<AuditEventView> events() {
        return auditEventQuery.list(tenantId, new AuditEventFilter(null, null, null, null), 0, 10).items();
    }

    @Test
    void should_return200AndWriteLoginSucceeded_whenPasswordCorrect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .with(request -> { request.setRemoteAddr("10.9.0.1"); return request; }))
            .andExpect(status().isOk());

        assertThat(events()).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.STAFF_LOGIN_SUCCEEDED.name());
            assertThat(e.actorId()).isEqualTo(staffId);
            assertThat(e.targetId()).isEqualTo(staffId);
            assertThat(e.targetLabel()).isEqualTo("Dr. Giris");
        });
    }

    @Test
    void should_return401AndWriteLoginFailed_whenPasswordWrong() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"yanlis-sifre\"}")
                .with(request -> { request.setRemoteAddr("10.9.0.2"); return request; }))
            .andExpect(status().isUnauthorized());

        assertThat(events()).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.STAFF_LOGIN_FAILED.name());
            assertThat(e.summary()).isEqualTo("Hatalı şifre");
            assertThat(e.targetId()).isEqualTo(staffId);
        });
    }
}
