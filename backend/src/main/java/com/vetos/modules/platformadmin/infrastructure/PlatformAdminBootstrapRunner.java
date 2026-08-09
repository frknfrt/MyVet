package com.vetos.modules.platformadmin.infrastructure;

import com.vetos.modules.platformadmin.domain.PlatformAdminUser;
import com.vetos.modules.platformadmin.domain.PlatformAdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uygulama her ayaga kalktiginda platform_admin_users bos ise, ortam
 * degiskenlerinden (PLATFORM_ADMIN_EMAIL/PASSWORD/FULL_NAME) tek bir
 * platform admin hesabi olusturur. Davet/coklu-admin yonetimi bilincli
 * olarak kapsam disi -- gercek ihtiyac olursa DB'den elle eklenir.
 */
@Component
public class PlatformAdminBootstrapRunner implements ApplicationRunner {

    private final PlatformAdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String bootstrapFullName;

    public PlatformAdminBootstrapRunner(
        PlatformAdminUserRepository repository,
        PasswordEncoder passwordEncoder,
        @Value("${platform-admin.bootstrap-email}") String bootstrapEmail,
        @Value("${platform-admin.bootstrap-password}") String bootstrapPassword,
        @Value("${platform-admin.bootstrap-full-name}") String bootstrapFullName
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapFullName = bootstrapFullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsAny()) {
            return;
        }
        repository.save(PlatformAdminUser.register(
            bootstrapEmail, passwordEncoder.encode(bootstrapPassword), bootstrapFullName
        ));
    }
}
