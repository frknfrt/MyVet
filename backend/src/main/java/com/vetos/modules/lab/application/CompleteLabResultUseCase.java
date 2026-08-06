package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.CompleteLabResultCommand;
import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultItem;
import com.vetos.modules.lab.domain.LabResultItemRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteLabResultUseCase {

    private final LabResultRepository labResultRepository;
    private final LabResultItemRepository labResultItemRepository;

    @Transactional
    public void execute(CompleteLabResultCommand command) {
        LabResult result = labResultRepository.findById(command.labResultId())
            .orElseThrow(() -> new LabResultNotFoundException(command.labResultId()));

        result.complete(command.resultSummary());
        labResultRepository.save(result);

        labResultItemRepository.deleteByLabResultId(result.getId());
        command.items().forEach(item -> labResultItemRepository.save(
            LabResultItem.create(result.getId(), item.parameterName(), item.value(), item.unit(), item.referenceRange(), item.flag())
        ));
    }
}
