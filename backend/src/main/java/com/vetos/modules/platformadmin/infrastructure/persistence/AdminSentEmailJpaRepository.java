package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AdminSentEmailJpaRepository extends JpaRepository<AdminSentEmail, UUID> {
    List<AdminSentEmail> findTop50ByOrderBySentAtDesc();
}
