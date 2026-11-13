package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Platform admin: vitrinden gelen deneme kayitlari (en yeni once). */
@Service
@RequiredArgsConstructor
public class ListTrialSignupRequestsUseCase {

    private final TrialSignupRequestRepository trialSignupRequestRepository;

    @Transactional(readOnly = true)
    public List<TrialSignupRequest> execute() {
        return trialSignupRequestRepository.findAllNewestFirst();
    }
}
