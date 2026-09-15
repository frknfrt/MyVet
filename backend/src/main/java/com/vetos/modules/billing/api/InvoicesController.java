package com.vetos.modules.billing.api;

import com.vetos.modules.billing.api.dto.*;
import com.vetos.modules.billing.application.*;
import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.application.dto.BranchComparisonLine;
import com.vetos.modules.billing.application.dto.ProductSalesLine;
import com.vetos.modules.billing.application.dto.RevenueReportLine;
import com.vetos.modules.billing.application.dto.StaffPerformanceLine;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import com.vetos.platform.web.CsvWriter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * api-conventions.md rol matrisi: /invoices/**, /payments/** -> sadece
 * RECEPTIONIST ve ADMIN.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
public class InvoicesController {

    private final ListInvoicesUseCase listInvoicesUseCase;
    private final GetInvoiceUseCase getInvoiceUseCase;
    private final GetInvoiceByBoardingStayUseCase getInvoiceByBoardingStayUseCase;
    private final GetRevenueSummaryUseCase getRevenueSummaryUseCase;
    private final GetTodaySalesSummaryUseCase getTodaySalesSummaryUseCase;
    private final GetRevenueReportUseCase getRevenueReportUseCase;
    private final GetProductSalesReportUseCase getProductSalesReportUseCase;
    private final GetStaffPerformanceReportUseCase getStaffPerformanceReportUseCase;
    private final GetBranchComparisonReportUseCase getBranchComparisonReportUseCase;
    private final CreateManualInvoiceUseCase createManualInvoiceUseCase;
    private final AddInvoiceLineUseCase addInvoiceLineUseCase;
    private final IssueInvoiceUseCase issueInvoiceUseCase;
    private final VoidInvoiceUseCase voidInvoiceUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final CompleteQuickSaleUseCase completeQuickSaleUseCase;

    @GetMapping
    public List<InvoiceSummaryResponse> list() {
        return listInvoicesUseCase.execute(TenantContext.current()).stream().map(InvoiceSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(getInvoiceUseCase.execute(id));
    }

    @GetMapping("/by-boarding-stay/{boardingStayId}")
    public InvoiceResponse getByBoardingStay(@PathVariable UUID boardingStayId) {
        return InvoiceResponse.from(getInvoiceByBoardingStayUseCase.execute(boardingStayId));
    }

    @GetMapping("/revenue-summary")
    public RevenueSummaryResponse revenueSummary() {
        return RevenueSummaryResponse.from(getRevenueSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/today-summary")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public TodaySalesSummaryResponse todaySummary() {
        return TodaySalesSummaryResponse.from(getTodaySalesSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/reports/revenue")
    public List<RevenueReportLineResponse> revenueReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        return getRevenueReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status).stream()
            .map(RevenueReportLineResponse::from)
            .toList();
    }

    @GetMapping(value = "/reports/revenue/export", produces = "text/csv")
    public ResponseEntity<String> exportRevenueReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        List<RevenueReportLine> lines = getRevenueReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);
        String csv = CsvWriter.toCsv(
            List.of("Fatura Tarihi", "Müşteri", "Şube", "Durum", "Tutar", "Tahsil Edilen"),
            lines,
            List.of(
                l -> fmt.format(l.issuedAt()),
                RevenueReportLine::ownerName,
                RevenueReportLine::branchName,
                l -> l.status().name(),
                l -> l.totalAmount().toPlainString(),
                l -> l.paidAmount().toPlainString()
            )
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ciro-raporu.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/reports/product-sales")
    public List<ProductSalesLineResponse> productSalesReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        return getProductSalesReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status).stream()
            .map(ProductSalesLineResponse::from)
            .toList();
    }

    @GetMapping(value = "/reports/product-sales/export", produces = "text/csv")
    public ResponseEntity<String> exportProductSalesReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        List<ProductSalesLine> lines = getProductSalesReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status);
        String csv = CsvWriter.toCsv(
            List.of("Ürün / Hizmet", "Adet", "Toplam Ciro"),
            lines,
            List.of(ProductSalesLine::description, l -> String.valueOf(l.totalQuantity()), l -> l.totalRevenue().toPlainString())
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=urun-hizmet-satis-raporu.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/reports/staff-performance")
    public List<StaffPerformanceLineResponse> staffPerformanceReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        return getStaffPerformanceReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status).stream()
            .map(StaffPerformanceLineResponse::from)
            .toList();
    }

    @GetMapping(value = "/reports/staff-performance/export", produces = "text/csv")
    public ResponseEntity<String> exportStaffPerformanceReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        List<StaffPerformanceLine> lines = getStaffPerformanceReportUseCase.execute(TenantContext.current(), range[0], range[1], branchId, status);
        String csv = CsvWriter.toCsv(
            List.of("Hekim", "Fatura Sayısı", "Toplam Ciro", "Ort. Fatura Tutarı"),
            lines,
            List.of(
                StaffPerformanceLine::staffName,
                l -> String.valueOf(l.invoiceCount()),
                l -> l.totalRevenue().toPlainString(),
                l -> l.avgInvoiceAmount().toPlainString()
            )
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hekim-performans-raporu.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/reports/branch-comparison")
    public List<BranchComparisonLineResponse> branchComparisonReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        return getBranchComparisonReportUseCase.execute(TenantContext.current(), range[0], range[1], status).stream()
            .map(BranchComparisonLineResponse::from)
            .toList();
    }

    @GetMapping(value = "/reports/branch-comparison/export", produces = "text/csv")
    public ResponseEntity<String> exportBranchComparisonReport(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) InvoiceStatus status
    ) {
        Instant[] range = resolveRange(from, to);
        List<BranchComparisonLine> lines = getBranchComparisonReportUseCase.execute(TenantContext.current(), range[0], range[1], status);
        String csv = CsvWriter.toCsv(
            List.of("Şube", "Fatura Sayısı", "Toplam Ciro", "Tahsil Edilen"),
            lines,
            List.of(
                BranchComparisonLine::branchName,
                l -> String.valueOf(l.invoiceCount()),
                l -> l.totalRevenue().toPlainString(),
                l -> l.paidRevenue().toPlainString()
            )
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sube-karsilastirma-raporu.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    private Instant[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : YearMonth.now().atDay(1);
        LocalDate effectiveToExclusive = (to != null ? to : LocalDate.now()).plusDays(1);
        return new Instant[] {
            effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
            effectiveToExclusive.atStartOfDay(ZoneOffset.UTC).toInstant()
        };
    }

    @PostMapping
    public ResponseEntity<Void> create(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid CreateInvoiceRequest request
    ) {
        UUID id = createManualInvoiceUseCase.execute(principal.branchIds().get(0), request.ownerId(), principal.staffUserId());
        return ResponseEntity.created(java.net.URI.create("/api/v1/invoices/" + id)).build();
    }

    @PostMapping("/quick-sale")
    public ResponseEntity<Void> quickSale(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid CompleteQuickSaleRequest request
    ) {
        List<QuickSaleLineCommand> lines = request.lines().stream()
            .map(l -> new QuickSaleLineCommand(l.inventoryItemId(), l.description(), l.quantity(), l.unitPrice(), l.vatRate()))
            .toList();
        UUID id = completeQuickSaleUseCase.execute(new CompleteQuickSaleCommand(
            principal.branchIds().get(0), request.ownerId(), principal.staffUserId(), lines, request.paymentMethod()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/invoices/" + id)).build();
    }

    @PostMapping("/{id}/lines")
    public void addLine(@PathVariable UUID id, @RequestBody @Valid AddInvoiceLineRequest request) {
        addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
            id, request.description(), request.quantity(), request.unitPrice(),
            request.discountAmount(), request.vatRate(), request.serviceTypeId(), request.inventoryItemId()
        ));
    }

    @PostMapping("/{id}/issue")
    public void issue(@PathVariable UUID id) {
        issueInvoiceUseCase.execute(id);
    }

    @PostMapping("/{id}/void")
    public void voidInvoice(@PathVariable UUID id) {
        voidInvoiceUseCase.execute(id);
    }

    @PostMapping("/{id}/payments")
    public void recordPayment(@PathVariable UUID id, @RequestBody @Valid RecordPaymentRequest request) {
        recordPaymentUseCase.execute(new RecordPaymentCommand(id, request.method(), request.amount(), request.pspRef()));
    }
}
