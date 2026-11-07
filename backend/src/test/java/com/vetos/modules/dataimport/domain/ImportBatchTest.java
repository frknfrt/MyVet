package com.vetos.modules.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetos.modules.dataimport.domain.ImportReport.EntityTotals;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportBatchTest {

    private static ImportReport report(int alreadyImported) {
        return new ImportReport(Map.of(ImportEntityType.OWNER, new EntityTotals(1, 1, 0, alreadyImported, 0)),
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ImportBatch previewed() {
        return ImportBatch.preview(UUID.randomUUID(), "KolayVet", "x.xlsx", new byte[]{1}, report(0), 1, UUID.randomUUID());
    }

    private static ImportReport piiReport() {
        return new ImportReport(Map.of(ImportEntityType.OWNER, new EntityTotals(2, 1, 1, 0, 1)),
            List.of(new ImportReport.RowIssue("Sahipler", 3, "K2", "Telefon geçersiz: 0532")),
            List.of(new ImportReport.RowIssue("Sahipler", 2, "K1", "Ali Veli ile eşleşti")),
            List.of(new ImportReport.OwnerMatch("K1", "Ali Veli", "5321234567", UUID.randomUUID(), "Ali Veli")),
            List.of("Kirpi"), List.of("yapisal"));
    }

    private static ImportBatch previewedWithPii() {
        return ImportBatch.preview(UUID.randomUUID(), "KolayVet", "x.xlsx", new byte[]{1}, piiReport(), 2, UUID.randomUUID());
    }

    private static void assertScrubbed(ImportReport r) {
        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).isEmpty();
        assertThat(r.ownerMatches()).isEmpty();
        assertThat(r.totals().get(ImportEntityType.OWNER)).isEqualTo(new EntityTotals(2, 1, 1, 0, 1));
        assertThat(r.newSpecies()).containsExactly("Kirpi");
        assertThat(r.structuralErrors()).containsExactly("yapisal");
    }

    private static ImportBatch committing() {
        ImportBatch b = previewed();
        b.startCommit(List.of("7"));
        return b;
    }

    @Test
    void should_markCommitted_onlyFromCommitting() {
        assertThatThrownBy(() -> previewed().markCommitted()).isInstanceOf(IllegalStateException.class);

        ImportBatch b = committing();
        b.markCommitted();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(b.getSourceFile()).isNull();
        assertThat(b.getCommittedAt()).isNotNull();
        assertThatThrownBy(b::markCommitted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_markFailed_onlyFromCommitting() {
        assertThatThrownBy(() -> previewed().markFailed("x")).isInstanceOf(IllegalStateException.class);

        ImportBatch b = committing();
        b.markFailed("bozuldu");
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(b.getErrorMessage()).isEqualTo("bozuldu");
        assertThat(b.getSourceFile()).isNull();
        assertThatThrownBy(() -> b.markFailed("tekrar")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_refreshReport_onlyWhileCommitting() {
        assertThatThrownBy(() -> previewed().refreshReport(report(5))).isInstanceOf(IllegalStateException.class);

        ImportBatch b = committing();
        b.refreshReport(report(5));
        assertThat(b.getReport().totals().get(ImportEntityType.OWNER).alreadyImported()).isEqualTo(5);
    }

    @Test
    void should_markRolledBack_fromCommitted() {
        ImportBatch b = committing();
        b.markCommitted();
        b.markRolledBack();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.ROLLED_BACK);
        assertThat(b.getRolledBackAt()).isNotNull();
        assertThatThrownBy(b::markRolledBack).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_markRolledBack_fromFailed() {
        ImportBatch b = committing();
        b.markFailed("temizlik basarisiz");
        b.markRolledBack();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.ROLLED_BACK);
    }

    @Test
    void should_notMarkRolledBack_fromOtherStates() {
        assertThatThrownBy(() -> previewed().markRolledBack()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> committing().markRolledBack()).isInstanceOf(IllegalStateException.class);
        ImportBatch discarded = previewed();
        discarded.discard();
        assertThatThrownBy(discarded::markRolledBack).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_scrubPiiFromReport_onDiscard() {
        ImportBatch b = previewedWithPii();
        b.discard();
        assertScrubbed(b.getReport());
    }

    @Test
    void should_scrubPiiFromReport_onFailed() {
        ImportBatch b = previewedWithPii();
        b.startCommit(List.of());
        b.markFailed("bozuldu");
        assertScrubbed(b.getReport());
    }

    @Test
    void should_scrubPiiFromReport_onRolledBack() {
        ImportBatch b = previewedWithPii();
        b.startCommit(List.of());
        b.markCommitted();
        b.markRolledBack();
        assertScrubbed(b.getReport());
    }

    @Test
    void should_keepFullReport_onCommitted() {
        ImportBatch b = previewedWithPii();
        b.startCommit(List.of());
        b.markCommitted();
        assertThat(b.getReport().errors()).hasSize(1);
        assertThat(b.getReport().warnings()).hasSize(1);
        assertThat(b.getReport().ownerMatches()).hasSize(1);
    }
}
