package com.vetos.modules.platformadmin.domain;

import java.util.List;

public interface AdminSentEmailRepository {
    AdminSentEmail save(AdminSentEmail entry);
    List<AdminSentEmail> findTop50ByOrderBySentAtDesc();
}
