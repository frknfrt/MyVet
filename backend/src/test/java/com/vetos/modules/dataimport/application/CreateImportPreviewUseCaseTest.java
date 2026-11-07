package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.application.excel.ImportWorkbookParser;
import com.vetos.modules.dataimport.application.excel.ParsedRow;
import com.vetos.modules.dataimport.application.excel.ParsedWorkbook;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportReport.EntityTotals;
import com.vetos.modules.dataimport.domain.exception.ImportTenantNotFoundException;
import com.vetos.modules.dataimport.domain.exception.InvalidImportFileException;
import com.vetos.modules.dataimport.domain.exception.InvalidImportRequestException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateImportPreviewUseCaseTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private TenantLookupPort tenants;
    private ImportWorkbookParser parser;
    private ImportValidator validator;
    private ExistingDataLookup lookup;
    private ImportBatchRepository batches;
    private CreateImportPreviewUseCase useCase;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantLookupPort.class);
        parser = mock(ImportWorkbookParser.class);
        validator = mock(ImportValidator.class);
        lookup = mock(ExistingDataLookup.class);
        batches = mock(ImportBatchRepository.class);
        when(batches.save(any(ImportBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(tenants.findTenantName(tenantId)).thenReturn(Optional.of("Klinik"));
        useCase = new CreateImportPreviewUseCase(tenants, parser, validator, lookup, batches);
    }

    private ImportReport report() {
        return new ImportReport(Map.of(ImportEntityType.OWNER, new EntityTotals(2, 2, 0, 0, 0)),
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void should_savePreviewedBatchWithReport() {
        ParsedWorkbook wb = new ParsedWorkbook(
            List.of(mock(ParsedRow.class), mock(ParsedRow.class)),
            List.of(mock(ParsedRow.class)), List.of());
        ImportReport report = report();
        when(parser.parse(any(InputStream.class))).thenReturn(wb);
        when(validator.validate(tenantId, "KolayVet", wb, lookup)).thenReturn(report);

        ImportBatch result = useCase.execute(tenantId, "KolayVet", "a.xlsx", new byte[]{1, 2}, adminId);

        ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
        assertThat(result.getReport()).isSameAs(report);
        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getCreatedByAdminId()).isEqualTo(adminId);
        assertThat(result.getSourceFile()).containsExactly(1, 2);
    }

    @Test
    void should_reuseExistingSourceSystemSpelling_whenFoldEqual() {
        ParsedWorkbook wb = new ParsedWorkbook(List.of(mock(ParsedRow.class)), List.of(), List.of());
        when(parser.parse(any(InputStream.class))).thenReturn(wb);
        when(validator.validate(tenantId, "KolayVet", wb, lookup)).thenReturn(report());
        when(batches.findSourceSystems(tenantId)).thenReturn(List.of("VetPro", "KolayVet"));

        ImportBatch lower = useCase.execute(tenantId, "kolayvet", "a.xlsx", new byte[]{1}, adminId);
        ImportBatch spaced = useCase.execute(tenantId, " Kolay Vet ", "b.xlsx", new byte[]{1}, adminId);
        ImportBatch tabbed = useCase.execute(tenantId, "KOLAY	VET", "c.xlsx", new byte[]{1}, adminId);

        assertThat(lower.getSourceSystem()).isEqualTo("KolayVet");
        assertThat(spaced.getSourceSystem()).isEqualTo("KolayVet");
        assertThat(tabbed.getSourceSystem()).isEqualTo("KolayVet");
        // De-dup (daha once aktarilmis) kontrolu de ayni yazimla yapilir.
        verify(validator, org.mockito.Mockito.times(3)).validate(tenantId, "KolayVet", wb, lookup);
    }

    @Test
    void should_keepNewSourceSystemSpelling_whenNoFoldEqualExists() {
        ParsedWorkbook wb = new ParsedWorkbook(List.of(mock(ParsedRow.class)), List.of(), List.of());
        when(parser.parse(any(InputStream.class))).thenReturn(wb);
        when(validator.validate(tenantId, "Kolay Vet 2", wb, lookup)).thenReturn(report());
        when(batches.findSourceSystems(tenantId)).thenReturn(List.of("KolayVet"));

        ImportBatch result = useCase.execute(tenantId, "Kolay Vet 2", "a.xlsx", new byte[]{1}, adminId);

        assertThat(result.getSourceSystem()).isEqualTo("Kolay Vet 2");
    }

    @Test
    void should_rejectBlankSourceSystem() {
        assertThatThrownBy(() -> useCase.execute(tenantId, "  ", "a.xlsx", new byte[]{1}, adminId))
            .isInstanceOf(InvalidImportRequestException.class);
        assertThatThrownBy(() -> useCase.execute(tenantId, "x".repeat(61), "a.xlsx", new byte[]{1}, adminId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(batches, never()).save(any());
    }

    @Test
    void should_failForUnknownTenant() {
        UUID other = UUID.randomUUID();
        when(tenants.findTenantName(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(other, "KolayVet", "a.xlsx", new byte[]{1}, adminId))
            .isInstanceOf(ImportTenantNotFoundException.class);
    }

    @Test
    void should_wrapRawParserFailureAsInvalidFile() {
        when(parser.parse(any(InputStream.class))).thenThrow(new IllegalStateException("bozuk hücre"));
        assertThatThrownBy(() -> useCase.execute(tenantId, "KolayVet", "a.xlsx", new byte[]{1}, adminId))
            .isInstanceOf(InvalidImportFileException.class)
            .hasMessageContaining("Dosya okunamadı")
            .hasMessageContaining("bozuk hücre");
        verify(validator, never()).validate(eq(tenantId), any(), any(), any());
    }
}
