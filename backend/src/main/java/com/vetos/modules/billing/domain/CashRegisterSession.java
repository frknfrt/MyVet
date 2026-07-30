package com.vetos.modules.billing.domain;

import com.vetos.modules.billing.domain.exception.CashRegisterInvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * er-diagram.mermaid'de tanimli degil -- requirements.md 4.7'deki "Kasa
 * yonetimi" MVP eklentisi icin, kullanici onayiyla basit bir gunluk
 * acilis/kapanis kaydi olarak modellendi (mutabakat/fark hesabi YOK,
 * sadece acilis ve kapanis tutari elle girilir).
 */
@Entity
@Table(name = "cash_register_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashRegisterSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "opened_by_staff_id", nullable = false)
    private UUID openedByStaffId;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_by_staff_id")
    private UUID closedByStaffId;

    @Column(name = "closing_balance")
    private BigDecimal closingBalance;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashRegisterStatus status;

    private String notes;

    public static CashRegisterSession open(UUID branchId, UUID staffId, BigDecimal openingBalance, String notes) {
        CashRegisterSession session = new CashRegisterSession();
        session.branchId = branchId;
        session.openedByStaffId = staffId;
        session.openingBalance = openingBalance;
        session.openedAt = Instant.now();
        session.status = CashRegisterStatus.OPEN;
        session.notes = notes;
        return session;
    }

    public void close(UUID staffId, BigDecimal closingBalance, String notes) {
        if (status != CashRegisterStatus.OPEN) {
            throw new CashRegisterInvalidStateException(id, "Kasa zaten kapali");
        }
        this.closedByStaffId = staffId;
        this.closingBalance = closingBalance;
        this.closedAt = Instant.now();
        this.status = CashRegisterStatus.CLOSED;
        this.notes = notes;
    }
}
