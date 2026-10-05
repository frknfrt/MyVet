package com.vetos.modules.platformadmin.domain;

import java.util.List;

public interface AnnouncementRepository {
    Announcement save(Announcement announcement);
    List<Announcement> findTop50ByOrderByCreatedAtDesc();
}
