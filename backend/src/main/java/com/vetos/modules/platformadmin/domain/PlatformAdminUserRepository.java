package com.vetos.modules.platformadmin.domain;

import java.util.Optional;

public interface PlatformAdminUserRepository {
    PlatformAdminUser save(PlatformAdminUser user);
    Optional<PlatformAdminUser> findByEmail(String email);
    boolean existsAny();
}
