package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.application.dto.BoardingStaySummary;
import com.vetos.modules.boarding.domain.BoardingRoom;
import com.vetos.modules.boarding.domain.BoardingRoomRepository;
import com.vetos.modules.boarding.domain.BoardingStay;
import com.vetos.modules.boarding.domain.BoardingStayRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListBoardingStaysUseCase {

    private final BoardingStayRepository boardingStayRepository;
    private final BoardingRoomRepository boardingRoomRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<BoardingStaySummary> execute(UUID tenantId) {
        return boardingStayRepository.findByTenantId(tenantId).stream()
            .map(this::toSummary)
            .toList();
    }

    private BoardingStaySummary toSummary(BoardingStay stay) {
        BoardingRoom room = boardingRoomRepository.findById(stay.getRoomId()).orElse(null);
        var patient = patientLookupPort.findSummaryById(stay.getPatientId());
        var owner = ownerLookupPort.findSummaryById(stay.getOwnerId());
        return new BoardingStaySummary(
            stay.getId(), stay.getRoomId(), room != null ? room.getName() : "—", room != null ? room.getGroupName() : "—",
            stay.getPatientId(), patient.name(), stay.getOwnerId(), owner.fullName(),
            stay.getCheckInDate(), stay.getExpectedCheckOutDate(), stay.getActualCheckOutDate(),
            stay.getStatus(), stay.getNotes()
        );
    }
}
