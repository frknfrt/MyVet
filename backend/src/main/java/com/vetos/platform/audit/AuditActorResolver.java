package com.vetos.platform.audit;

import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Guvenlik baglamindaki kimligi aktore cevirir. Ad burada cozulmez (AuditPortAdapter doldurur). */
@Component
public class AuditActorResolver {

    private static final String EXTENSION_AUTHORITY = "ROLE_" + ExtensionTokenAuthenticationFilter.ROLE;

    public AuditActor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (principal instanceof AuthenticatedStaffUser staff) {
            boolean extension = auth.getAuthorities().stream().anyMatch(a -> EXTENSION_AUTHORITY.equals(a.getAuthority()));
            return new AuditActor(AuditActor.ActorType.STAFF, staff.staffUserId(), null, staff.role(),
                staff.impersonatedBy(),
                extension ? AuditActor.Channel.EXTENSION : AuditActor.Channel.WEB);
        }
        if (principal instanceof AuthenticatedPlatformAdmin admin) {
            return AuditActor.platformAdmin(admin.platformAdminId(), admin.email());
        }
        throw new IllegalStateException("Denetim kaydi icin kimlik yok -- acik aktorlu AuditPort.record(record, actor) kullanin");
    }
}
