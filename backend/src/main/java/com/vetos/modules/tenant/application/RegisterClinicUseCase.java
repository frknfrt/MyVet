package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.AuthSession;
import com.vetos.modules.tenant.application.dto.RegisterClinicCommand;
import com.vetos.modules.tenant.domain.*;
import com.vetos.modules.tenant.domain.event.ClinicRegisteredEvent;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Yeni bir klinik (tenant) + ilk sube + ADMIN rolunde ilk personel kaydini
 * tek islemde olusturur ve otomatik giris icin bir JWT uretir.
 */
@Service
@RequiredArgsConstructor
public class RegisterClinicUseCase {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public AuthSession execute(RegisterClinicCommand command) {
        if (staffUserRepository.existsByEmail(command.adminEmail())) {
            throw new EmailAlreadyRegisteredConflictException(command.adminEmail());
        }

        Tenant tenant = tenantRepository.save(Tenant.register(command.tenantName(), command.taxNumber()));
        Branch branch = branchRepository.save(Branch.create(tenant.getId(), command.branchName()));
        subscriptionRepository.save(Subscription.startTrial(tenant.getId()));

        String passwordHash = passwordEncoder.encode(command.adminPassword());
        StaffUser admin = staffUserRepository.save(
            StaffUser.register(branch.getId(), command.adminFullName(), command.adminEmail(), passwordHash, StaffRole.ADMIN)
        );

        eventPublisher.publish(new ClinicRegisteredEvent(tenant.getId(), branch.getId(), admin.getId()));

        String token = jwtTokenProvider.generateToken(
            admin.getId(), tenant.getId(), List.of(branch.getId()), admin.getRole().name()
        );
        return new AuthSession(token, admin.getId(), tenant.getId(), branch.getId(), admin.getFullName(), admin.getRole());
    }
}
