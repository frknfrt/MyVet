package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dashboard'daki "Bugunku Satis" KPI karti icin -- GetRevenueSummaryUseCase
 * ile ayni desen ama SADECE bugunun toplamini dondurur ve tum rollere acik
 * (bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md S4.5).
 */
@Service
@RequiredArgsConstructor
public class GetTodaySalesSummaryUseCase {

    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public TodaySalesSummary execute(UUID tenantId) {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = startOfToday.plusSeconds(86400);

        SalesAggregate aggregate = invoiceRepository.sumIssuedBetween(
            tenantId, REVENUE_STATUSES, startOfToday, startOfTomorrow
        );
        return new TodaySalesSummary(aggregate.totalAmount(), Math.toIntExact(aggregate.count()));
    }
}
