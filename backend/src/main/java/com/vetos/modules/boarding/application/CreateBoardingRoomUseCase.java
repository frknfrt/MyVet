package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.application.dto.CreateBoardingRoomCommand;
import com.vetos.modules.boarding.domain.BoardingRoom;
import com.vetos.modules.boarding.domain.BoardingRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBoardingRoomUseCase {

    private final BoardingRoomRepository boardingRoomRepository;

    @Transactional
    public UUID execute(CreateBoardingRoomCommand command) {
        BoardingRoom room = BoardingRoom.create(
            command.tenantId(), command.branchId(), command.groupName(), command.name(),
            command.capacity(), command.dailyRate(), command.notes()
        );
        return boardingRoomRepository.save(room).getId();
    }
}
