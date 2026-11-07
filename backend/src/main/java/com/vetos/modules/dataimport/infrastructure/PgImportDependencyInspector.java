package com.vetos.modules.dataimport.infrastructure;

import com.vetos.modules.dataimport.domain.Dependent;
import com.vetos.modules.dataimport.domain.ImportDependencyInspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * pg_constraint uzerinden {@code <table>}'a referans veren TUM yabanci anahtarlari kesfeder (yeni bir tablo eklenince
 * kod degismez) ve her biri icin bagimli satirlari arar. Desteklenmeyen FK (bilesik, id disi ya da ON DELETE
 * NO ACTION/RESTRICT disi) bulunursa IllegalStateException ile durur (fail-closed).
 *
 * Guvenlik: tablo adi sabit izin listesinden ({owners, patients}); referans veren tablo/sutun/sema adlari katalogdan
 * gelir ve identifier olarak tirnaklanir; kimlikler parametre olarak gonderilir (SQL'e eklenmez).
 * Referans veren tabloda {@code id} sutunu yoksa her bagimli satir engel sayilir ve rowId null doner.
 * Rapor sismesin diye en fazla {@value #MAX_BLOCKERS} engel dondurulur (biri bile geri almayi durdurur).
 */
@Component
@RequiredArgsConstructor
class PgImportDependencyInspector implements ImportDependencyInspector {

    private static final Set<String> ALLOWED_TABLES = Set.of("owners", "patients");
    /** pg_constraint.confdeltype: 'a' NO ACTION, 'r' RESTRICT. */
    private static final Set<String> SUPPORTED_DELETE_ACTIONS = Set.of("a", "r");
    static final int BATCH_SIZE = 1000;
    static final int MAX_BLOCKERS = 100;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<Dependent> findForeignDependents(String table, Collection<UUID> ids, Collection<UUID> excludedIds) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Desteklenmeyen tablo: " + table);
        }
        List<Dependent> blockers = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return blockers;
        }
        String excluded = arrayLiteral(excludedIds == null ? List.of() : excludedIds);
        List<UUID> all = new ArrayList<>(ids);
        for (Reference ref : references(table)) {
            for (int from = 0; from < all.size() && blockers.size() < MAX_BLOCKERS; from += BATCH_SIZE) {
                List<UUID> chunk = all.subList(from, Math.min(all.size(), from + BATCH_SIZE));
                blockers.addAll(query(ref, arrayLiteral(chunk), excluded, MAX_BLOCKERS - blockers.size()));
            }
        }
        return blockers;
    }

    /**
     * Tablo'ya referans veren TUM yabanci anahtarlar okunur; desteklenmeyen biri varsa fail-closed: hata firlatilir.
     * Desteklenen: tek sutunlu, {@code id}'ye referansli, ON DELETE NO ACTION ('a') ya da RESTRICT ('r').
     * Bilesik / id disi FK burada aranamaz; CASCADE / SET NULL / SET DEFAULT ise silme sirasinda klinik verisini
     * sessizce siler ya da koparir (veritabani silmeyi reddetmez). Ikisinde de "engel yok" sonucu guvenilmez olurdu.
     * Bu kural ImportForeignKeySchemaGuardTest ile build'de de korunur.
     */
    private List<Reference> references(String table) {
        List<ForeignKey> fks = jdbc.query(
            "SELECT ns.nspname AS schema_name, cl.relname AS table_name, c.conname AS constraint_name, "
                + "cardinality(c.conkey) AS column_count, c.confdeltype::text AS on_delete, "
                + "a.attname AS column_name, ra.attname AS referenced_column, "
                + "EXISTS (SELECT 1 FROM pg_attribute i WHERE i.attrelid = c.conrelid AND i.attname = 'id' "
                + "AND NOT i.attisdropped AND i.attnum > 0) AS has_id "
                + "FROM pg_constraint c "
                + "JOIN pg_class cl ON cl.oid = c.conrelid "
                + "JOIN pg_namespace ns ON ns.oid = cl.relnamespace "
                + "JOIN pg_class ref ON ref.oid = c.confrelid "
                + "JOIN pg_namespace rns ON rns.oid = ref.relnamespace "
                + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] "
                + "JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1] "
                + "WHERE c.contype = 'f' AND ref.relname = :table AND rns.nspname = current_schema() "
                + "ORDER BY cl.relname, a.attname",
            Map.of("table", table),
            (rs, i) -> new ForeignKey(rs.getString("schema_name"), rs.getString("table_name"),
                rs.getString("constraint_name"), rs.getInt("column_count"), rs.getString("on_delete"),
                rs.getString("column_name"), rs.getString("referenced_column"), rs.getBoolean("has_id")));
        List<Reference> refs = new ArrayList<>();
        for (ForeignKey fk : fks) {
            boolean supported = fk.columnCount() == 1 && "id".equals(fk.referencedColumn())
                && SUPPORTED_DELETE_ACTIONS.contains(fk.onDelete());
            if (!supported) {
                throw new IllegalStateException("Desteklenmeyen yabancı anahtar: " + fk.schema() + "." + fk.table()
                    + " (" + fk.constraint() + ") -> " + table + " [sütun sayısı=" + fk.columnCount()
                    + ", hedef sütun=" + fk.referencedColumn() + ", ON DELETE=" + fk.onDelete()
                    + "]; geri alma güvenle denetlenemez");
            }
            refs.add(new Reference(fk.schema(), fk.table(), fk.column(), fk.hasId()));
        }
        return refs;
    }

    private List<Dependent> query(Reference ref, String idsLiteral, String excludedLiteral, int limit) {
        String from = quote(ref.schema()) + "." + quote(ref.table());
        String column = quote(ref.column());
        String sql = ref.hasId()
            ? "SELECT id FROM " + from + " WHERE " + column + " = ANY (CAST(:ids AS uuid[])) "
                + "AND NOT (id = ANY (CAST(:excluded AS uuid[]))) LIMIT :limit"
            : "SELECT CAST(NULL AS uuid) AS id FROM " + from + " WHERE " + column
                + " = ANY (CAST(:ids AS uuid[])) LIMIT :limit";
        Map<String, Object> params = ref.hasId()
            ? Map.of("ids", idsLiteral, "excluded", excludedLiteral, "limit", limit)
            : Map.of("ids", idsLiteral, "limit", limit);
        return jdbc.query(sql, params,
            (rs, i) -> new Dependent(ref.table(), rs.getObject("id", UUID.class), ref.column()));
    }

    private static String arrayLiteral(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record Reference(String schema, String table, String column, boolean hasId) {
    }

    private record ForeignKey(String schema, String table, String constraint, int columnCount, String onDelete,
                              String column, String referencedColumn, boolean hasId) {
    }
}
