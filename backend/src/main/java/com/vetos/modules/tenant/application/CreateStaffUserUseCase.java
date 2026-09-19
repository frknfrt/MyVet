package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.CreateStaffUserCommand;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateStaffUserUseCase {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UUID execute(CreateStaffUserCommand command) {
        // staff_users.email GLOBAL essiz (tenant-bazli degil). StaffUser artik
        // @TenantId'li oldugundan, bu existsByEmail sorgusu cagiranin KENDI
        // context'inde calisirsa sessizce o kiraciyla filtrelenir ve baska
        // kiracidaki cakisan bir kaydi KACIRIR -- kontrol sonra global DB
        // kisitina carpar ve beklenmedik 500 (DataIntegrityViolationException)
        // olarak yuzeye cikar. Kontrolu kasitli olarak root Session'da
        // calistirip cagiranin context'ini hemen sonra geri yukluyoruz.
        boolean emailTaken = TenantContext.callInRootSession(() -> staffUserRepository.existsByEmail(command.email()));
        if (emailTaken) {
            throw new EmailAlreadyRegisteredConflictException(command.email());
        }

        StaffUser staffUser = StaffUser.register(
            TenantContext.current(), command.branchId(), command.fullName(), command.email(),
            passwordEncoder.encode(command.password()), command.role()
        );
        staffUser.updateProfile(command.fullName(), command.phone(), command.licenseNumber(), command.specialty(), command.bio());

        return staffUserRepository.save(staffUser).getId();
    }
}
