package com.vetos.modules.dataimport.infrastructure;

import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordDeleter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native SQL ile silme: patient modulune delete metodu eklenmez (modul siniri). Her sorgu kiraci kosulunu
 * hem baglanti hem hedef tablo icin acikca tasir; JDBC, cagiranin Spring transaction'ina katilir.
 */
@Component
@RequiredArgsConstructor
class JdbcImportRecordDeleter implements ImportRecordDeleter {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public int deleteCreatedPatients(UUID tenantId, UUID batchId) {
        return deleteTargets("patients", ImportEntityType.PATIENT, tenantId, batchId);
    }

    @Override
    public int deleteCreatedOwners(UUID tenantId, UUID batchId) {
        return deleteTargets("owners", ImportEntityType.OWNER, tenantId, batchId);
    }

    @Override
    public int deleteLinks(UUID tenantId, UUID batchId) {
        return jdbc.update("DELETE FROM import_record_links WHERE tenant_id = :tenantId AND batch_id = :batchId",
            Map.of("tenantId", tenantId, "batchId", batchId));
    }

    private int deleteTargets(String table, ImportEntityType type, UUID tenantId, UUID batchId) {
        // table sabit (yukaridaki iki cagri), kullanicidan gelmez.
        return jdbc.update(
            "DELETE FROM " + table + " t USING import_record_links l "
                + "WHERE l.batch_id = :batchId AND l.tenant_id = :tenantId AND l.entity_type = :type "
                + "AND l.created_by_import = true AND t.id = l.target_id AND t.tenant_id = :tenantId",
            Map.of("tenantId", tenantId, "batchId", batchId, "type", type.name()));
    }
}
