package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Announcement;
import com.vetos.modules.platformadmin.domain.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListAnnouncementsUseCase {

    private final AnnouncementRepository announcementRepository;

    @Transactional(readOnly = true)
    public List<Announcement> execute() {
        return announcementRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
