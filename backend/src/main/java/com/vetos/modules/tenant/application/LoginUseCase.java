package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.AuthSession;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.TenantStatus;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.modules.tenant.domain.exception.TenantSuspendedForbiddenException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditPort auditPort;

    @Transactional(readOnly = true)
    public AuthSession execute(LoginCommand command) {
        StaffUser staffUser = staffUserRepository.findByEmail(command.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), staffUser.getPasswordHash())) {
            recordLogin(staffUser, AuditAction.STAFF_LOGIN_FAILED, "Hatalı şifre");
            throw new InvalidCredentialsException();
        }

        if (!staffUser.isActive()) {
            recordLogin(staffUser, AuditAction.STAFF_LOGIN_FAILED, "Pasif kullanıcı");
            throw new InvalidCredentialsException();
        }

        Branch branch = branchRepository.findById(staffUser.getBranchId())
            .orElseThrow(InvalidCredentialsException::new);

        Tenant tenant = tenantRepository.findById(branch.getTenantId())
            .orElseThrow(InvalidCredentialsException::new);

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            recordLogin(staffUser, AuditAction.STAFF_LOGIN_FAILED, "Askıdaki klinik");
            throw new TenantSuspendedForbiddenException();
        }

        String token = jwtTokenProvider.generateToken(
            staffUser.getId(), branch.getTenantId(), List.of(branch.getId()), staffUser.getRole().name()
        );
        recordLogin(staffUser, AuditAction.STAFF_LOGIN_SUCCEEDED, null);
        return new AuthSession(
            token, staffUser.getId(), branch.getTenantId(), branch.getId(), staffUser.getFullName(), staffUser.getRole()
        );
    }

    /** Giris olaylari kendi transaction'inda: bu metot readOnly tx'te calisir ve hata dallari istisnayla biter. */
    private void recordLogin(StaffUser staffUser, AuditAction action, String summary) {
        auditPort.recordStandalone(
            AuditRecord.of(action, "STAFF", staffUser.getId(), staffUser.getFullName(), summary).forTenant(staffUser.getTenantId()),
            AuditActor.staff(staffUser.getId(), staffUser.getFullName(), staffUser.getRole().name())
        );
    }
}
