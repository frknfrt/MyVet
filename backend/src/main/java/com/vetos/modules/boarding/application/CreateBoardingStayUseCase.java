package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.application.dto.CreateBoardingStayCommand;
import com.vetos.modules.boarding.domain.BoardingRoom;
import com.vetos.modules.boarding.domain.BoardingRoomRepository;
import com.vetos.modules.boarding.domain.BoardingStay;
import com.vetos.modules.boarding.domain.BoardingStayRepository;
import com.vetos.modules.boarding.domain.event.BoardingStayCreatedEvent;
import com.vetos.modules.boarding.domain.exception.BoardingRoomNotFoundException;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBoardingStayUseCase {

    private final BoardingStayRepository boardingStayRepository;
    private final BoardingRoomRepository boardingRoomRepository;
    private final PatientLookupPort patientLookupPort;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public UUID execute(CreateBoardingStayCommand command) {
        BoardingRoom room = boardingRoomRepository.findById(command.roomId())
            .orElseThrow(() -> new BoardingRoomNotFoundException(command.roomId()));
        var patient = patientLookupPort.findSummaryById(command.patientId());

        BoardingStay stay = BoardingStay.create(
            command.tenantId(), command.branchId(), command.roomId(), command.patientId(), patient.ownerId(),
            command.createdByStaffId(), command.checkInDate(), command.expectedCheckOutDate(), command.notes()
        );
        boardingStayRepository.save(stay);

        eventPublisher.publish(new BoardingStayCreatedEvent(
            stay.getId(), command.tenantId(), command.branchId(), patient.ownerId(),
            room.getGroupName() + " · " + room.getName(), room.getDailyRate(),
            command.checkInDate(), command.expectedCheckOutDate()
        ));

        return stay.getId();
    }
}
