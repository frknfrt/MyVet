package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.ImpersonationSession;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.exception.ImpersonationTargetNotFoundException;
import com.vetos.modules.tenant.domain.ImpersonationTarget;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin'in bir kiracinin ADMIN rolundeki personeli YERINE gecerek
 * (sifresini bilmeden) o kiracinin panelini aynen gormesini saglar --
 * destek/sorun giderme icin. Her impersonasyon artik AuditLogEntry'ye kalici
 * olarak yaziliyor (bkz. RecordAuditLogUseCase); WARN log satiri da ek bir
 * guvenlik agi olarak korundu. Uretilen JWT, normal bir staff girisininkiyle
 * AYNI -- JwtTokenProvider'a ayri bir "impersonation" claim turu EKLENMEDI
 * (dogrulanmis staff kimlik dogrulama yoluna dokunulmadi).
 */
@Service
@RequiredArgsConstructor
public class StartImpersonationUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartImpersonationUseCase.class);

    private final TenantAdminPort tenantAdminPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    public ImpersonationSession execute(UUID tenantId, UUID platformAdminId, String platformAdminEmail) {
        ImpersonationTarget target = tenantAdminPort.findImpersonationTarget(tenantId)
            .orElseThrow(() -> new ImpersonationTargetNotFoundException(tenantId));

        String token = jwtTokenProvider.generateToken(
            target.staffUserId(), tenantId, List.of(target.branchId()), target.role().name()
        );

        log.warn(
            "IMPERSONATION baslatildi: platformAdmin={} ({}), tenantId={}, impersonatedStaffUserId={}, role={}",
            platformAdminEmail, platformAdminId, tenantId, target.staffUserId(), target.role()
        );
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.TENANT_IMPERSONATED, "TENANT", tenantId,
            "impersonatedStaffUserId=" + target.staffUserId() + ", role=" + target.role()
        );

        return new ImpersonationSession(
            token, target.staffUserId(), tenantId, target.branchId(), target.fullName(), target.role().name()
        );
    }
}
