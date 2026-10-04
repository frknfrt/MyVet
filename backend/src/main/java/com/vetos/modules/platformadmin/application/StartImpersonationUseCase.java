package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.ImpersonationSession;
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
 * destek/sorun giderme icin. Henuz kalici bir audit_log tablosu yok (bkz.
 * docs/architecture.md, "Audit log" plani -- yol haritasinda ayri bir
 * madde); bu yuzden her impersonasyon en azindan sunucu logunda WARN
 * seviyesinde izlenebilir sekilde kayit birakir. Uretilen JWT, normal bir
 * staff girisininkiyle AYNI -- JwtTokenProvider'a ayri bir "impersonation"
 * claim turu EKLENMEDI (dogrulanmis staff kimlik dogrulama yoluna
 * dokunulmadi); tam izlenebilirlik icin audit_log eklendiginde bu kayit
 * kalici hale getirilmeli.
 */
@Service
@RequiredArgsConstructor
public class StartImpersonationUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartImpersonationUseCase.class);

    private final TenantAdminPort tenantAdminPort;
    private final JwtTokenProvider jwtTokenProvider;

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

        return new ImpersonationSession(
            token, target.staffUserId(), tenantId, target.branchId(), target.fullName(), target.role().name()
        );
    }
}
