package com.vetos;

import com.vetos.modules.dataimport.domain.ImportDependencyInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sema korumasi (veri aktarimi geri alma guvenligi): owners/patients'a referans veren TUM yabanci anahtarlar
 * tek sutunlu, {@code id}'ye referansli ve ON DELETE NO ACTION/RESTRICT olmalidir.
 *
 * Geri alma ve basarisiz aktarim temizligi, klinigin bu kayitlara bagladigi satirlari bu kurala guvenerek bulur ya da
 * veritabaninin silmeyi reddetmesine guvenir. CASCADE/SET NULL ekleyen bir migration klinik verisini sessizce
 * silebilir/koparabilir: bu test o migration'i build'de durdurur. Kurala uyan yeni FK eklemek serbesttir.
 * Yalnizca katalog okunur; veri yazilmaz.
 */
@SpringBootTest
class ImportForeignKeySchemaGuardTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImportDependencyInspector inspector;

    @Test
    void should_onlyHaveSingleColumnNoActionForeignKeysToOwnersAndPatients() {
        List<Map<String, Object>> fks = jdbc.queryForList(
            "SELECT ref.relname AS referenced, cl.relname AS referencing, c.conname AS name, "
                + "cardinality(c.conkey) AS columns, "
                + "(SELECT ra.attname FROM pg_attribute ra WHERE ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]) "
                + "AS referenced_column, c.confdeltype::text AS on_delete "
                + "FROM pg_constraint c "
                + "JOIN pg_class cl ON cl.oid = c.conrelid "
                + "JOIN pg_class ref ON ref.oid = c.confrelid "
                + "JOIN pg_namespace rns ON rns.oid = ref.relnamespace "
                + "WHERE c.contype = 'f' AND ref.relname IN ('owners', 'patients') AND rns.nspname = current_schema()");

        assertThat(fks).as("owners/patients'a referans veren yabanci anahtar bulunamadi (sorgu bozuk?)").isNotEmpty();
        assertThat(fks).allSatisfy(fk -> {
            String where = fk.get("referencing") + "." + fk.get("name") + " -> " + fk.get("referenced");
            assertThat(((Number) fk.get("columns")).intValue()).as("tek sutunlu olmali: " + where).isEqualTo(1);
            assertThat(String.valueOf(fk.get("referenced_column"))).as("id'ye referans vermeli: " + where)
                .isEqualTo("id");
            assertThat(String.valueOf(fk.get("on_delete"))).as("ON DELETE NO ACTION/RESTRICT olmali: " + where)
                .isIn("a", "r");
        });

        // Ayni kural uygulamada fail-closed: denetci bu semada hata vermemeli.
        inspector.findForeignDependents("owners", List.of(UUID.randomUUID()), List.of());
        inspector.findForeignDependents("patients", List.of(UUID.randomUUID()), List.of());
    }
}
