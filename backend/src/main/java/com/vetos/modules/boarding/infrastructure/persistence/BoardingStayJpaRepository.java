package com.vetos.modules.boarding.infrastructure.persistence;

import com.vetos.modules.boarding.domain.BoardingStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BoardingStayJpaRepository extends JpaRepository<BoardingStay, UUID> {
    List<BoardingStay> findByTenantId(UUID tenantId);
    List<BoardingStay> findByRoomId(UUID roomId);
}
