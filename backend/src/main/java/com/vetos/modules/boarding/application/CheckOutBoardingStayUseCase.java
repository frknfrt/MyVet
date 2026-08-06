package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.domain.BoardingStay;
import com.vetos.modules.boarding.domain.BoardingStayRepository;
import com.vetos.modules.boarding.domain.exception.BoardingStayNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckOutBoardingStayUseCase {

    private final BoardingStayRepository boardingStayRepository;

    @Transactional
    public void execute(UUID id, LocalDate actualCheckOutDate) {
        BoardingStay stay = boardingStayRepository.findById(id)
            .orElseThrow(() -> new BoardingStayNotFoundException(id));
        stay.checkOut(actualCheckOutDate);
        boardingStayRepository.save(stay);
    }
}
