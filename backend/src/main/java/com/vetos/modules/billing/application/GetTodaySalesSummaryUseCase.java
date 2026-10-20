package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dashboard'daki "Bugunku Satis" KPI karti icin -- GetRevenueSummaryUseCase
 * ile ayni desen ama SADECE bugunun toplamini dondurur ve tum rollere acik
 * (bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md S4.5).
 * Gun sinirlari Istanbul saatiyle (Faz 6).
 */
@Service
public class GetTodaySalesSummaryUseCase {

    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    @Autowired
    public GetTodaySalesSummaryUseCase(InvoiceRepository invoiceRepository) {
        this(invoiceRepository, Clock.system(ISTANBUL));
    }

    GetTodaySalesSummaryUseCase(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodaySalesSummary execute(UUID tenantId) {
        LocalDate today = LocalDate.now(clock.withZone(ISTANBUL));
        Instant startOfToday = today.atStartOfDay(ISTANBUL).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        SalesAggregate aggregate = invoiceRepository.sumIssuedBetween(
            tenantId, REVENUE_STATUSES, startOfToday, startOfTomorrow
        );
        return new TodaySalesSummary(aggregate.totalAmount(), Math.toIntExact(aggregate.count()));
    }
}
