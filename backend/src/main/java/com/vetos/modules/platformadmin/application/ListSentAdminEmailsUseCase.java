package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import com.vetos.modules.platformadmin.domain.AdminSentEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSentAdminEmailsUseCase {

    private final AdminSentEmailRepository adminSentEmailRepository;

    @Transactional(readOnly = true)
    public List<AdminSentEmail> execute() {
        return adminSentEmailRepository.findTop50ByOrderBySentAtDesc();
    }
}
