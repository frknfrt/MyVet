package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Announcement;
import com.vetos.modules.platformadmin.domain.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class AnnouncementRepositoryAdapter implements AnnouncementRepository {

    private final AnnouncementJpaRepository jpaRepository;

    @Override
    public Announcement save(Announcement announcement) { return jpaRepository.save(announcement); }

    @Override
    public List<Announcement> findTop50ByOrderByCreatedAtDesc() { return jpaRepository.findTop50ByOrderByCreatedAtDesc(); }
}
