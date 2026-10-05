package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AnnouncementJpaRepository extends JpaRepository<Announcement, UUID> {
    List<Announcement> findTop50ByOrderByCreatedAtDesc();
}
