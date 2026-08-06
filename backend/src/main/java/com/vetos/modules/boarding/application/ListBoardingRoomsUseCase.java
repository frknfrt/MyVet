package com.vetos.modules.boarding.application;

import com.vetos.modules.boarding.application.dto.BoardingRoomSummary;
import com.vetos.modules.boarding.domain.BoardingRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListBoardingRoomsUseCase {

    private final BoardingRoomRepository boardingRoomRepository;

    @Transactional(readOnly = true)
    public List<BoardingRoomSummary> execute(UUID tenantId) {
        return boardingRoomRepository.findByTenantId(tenantId).stream()
            .map(r -> new BoardingRoomSummary(r.getId(), r.getGroupName(), r.getName(), r.getCapacity(), r.getDailyRate(), r.getNotes(), r.isActive()))
            .toList();
    }
}
