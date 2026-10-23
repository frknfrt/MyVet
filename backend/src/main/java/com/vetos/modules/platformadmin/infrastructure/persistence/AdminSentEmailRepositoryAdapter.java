package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import com.vetos.modules.platformadmin.domain.AdminSentEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class AdminSentEmailRepositoryAdapter implements AdminSentEmailRepository {

    private final AdminSentEmailJpaRepository jpaRepository;

    @Override
    public AdminSentEmail save(AdminSentEmail entry) { return jpaRepository.save(entry); }

    @Override
    public List<AdminSentEmail> findTop50ByOrderBySentAtDesc() { return jpaRepository.findTop50ByOrderBySentAtDesc(); }
}
