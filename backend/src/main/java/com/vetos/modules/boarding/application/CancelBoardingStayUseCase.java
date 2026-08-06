package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.domain.BoardingStay;
import com.vetos.modules.boarding.domain.BoardingStayRepository;
import com.vetos.modules.boarding.domain.exception.BoardingStayNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelBoardingStayUseCase {

    private final BoardingStayRepository boardingStayRepository;

    @Transactional
    public void execute(UUID id) {
        BoardingStay stay = boardingStayRepository.findById(id)
            .orElseThrow(() -> new BoardingStayNotFoundException(id));
        stay.cancel();
        boardingStayRepository.save(stay);
    }
}
