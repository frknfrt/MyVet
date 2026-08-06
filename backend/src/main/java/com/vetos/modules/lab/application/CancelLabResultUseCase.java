package com.vetos.modules.lab.application;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelLabResultUseCase {

    private final LabResultRepository labResultRepository;

    @Transactional
    public void execute(UUID labResultId) {
        LabResult result = labResultRepository.findById(labResultId)
            .orElseThrow(() -> new LabResultNotFoundException(labResultId));
        result.cancel();
        labResultRepository.save(result);
    }
}
