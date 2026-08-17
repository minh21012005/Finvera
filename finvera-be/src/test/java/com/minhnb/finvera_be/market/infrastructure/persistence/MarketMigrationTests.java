package com.minhnb.finvera_be.market.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MarketMigrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("finvera")
            .withUsername("finvera")
            .withPassword(java.util.UUID.randomUUID().toString());

    @BeforeAll
    static void migrateFromEmpty() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsAllAuthoritativeMarketTablesAndSeedsFourIndices() throws Exception {
        try (var connection = connection();
                var tables = connection.prepareStatement("""
                        select count(*) from information_schema.tables
                        where table_schema = 'public' and table_name in (
                          'market_index', 'market_calendar_day', 'market_session_window',
                          'market_instrument', 'market_import_batch', 'ingestion_record',
                          'index_snapshot', 'equity_price_observation', 'breadth_snapshot',
                          'breadth_snapshot_input', 'regime_assessment', 'regime_factor',
                          'regime_assessment_input')
                        """);
                var indexCount = connection.prepareStatement("select count(*) from market_index")) {
            try (var result = tables.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(13);
            }
            try (var result = indexCount.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }

    @Test
    void preservesExactDecimalsAndUtcInstants() throws Exception {
        UUID ingestionId = insertAcceptedIngestion("VN_INDEX", "precision-fixture");
        UUID indexId;
        try (var connection = connection();
                var findIndex = connection.prepareStatement("select id from market_index where code = 'VN_INDEX'")) {
            try (var result = findIndex.executeQuery()) {
                result.next();
                indexId = result.getObject(1, UUID.class);
            }
            try (var insert = connection.prepareStatement("""
                    insert into index_snapshot (
                      id, index_id, ingestion_record_id, trading_date, observed_at,
                      accepted_at, session_state, index_level, reference_level,
                      absolute_change, percentage_change, matched_volume,
                      matched_value_vnd, source, revision)
                    values (?, ?, ?, '2026-08-17', ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, 'FINVERA_FIXTURE', 1)
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, indexId);
                insert.setObject(3, ingestionId);
                insert.setObject(4, OffsetDateTime.of(2026, 8, 17, 3, 0, 0, 0, ZoneOffset.UTC));
                insert.setObject(5, OffsetDateTime.of(2026, 8, 17, 3, 0, 1, 0, ZoneOffset.UTC));
                insert.setBigDecimal(6, new BigDecimal("1280.250000"));
                insert.setBigDecimal(7, new BigDecimal("1275.000000"));
                insert.setBigDecimal(8, new BigDecimal("5.250000"));
                insert.setBigDecimal(9, new BigDecimal("0.411765"));
                insert.setLong(10, 420_000_000L);
                insert.setBigDecimal(11, new BigDecimal("11250000000000.0000"));
                insert.executeUpdate();
            }
            try (var query = connection.prepareStatement("""
                    select index_level, matched_value_vnd, observed_at
                    from index_snapshot where ingestion_record_id = ?
                    """)) {
                query.setObject(1, ingestionId);
                try (var result = query.executeQuery()) {
                    result.next();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("1280.250000");
                    assertThat(result.getBigDecimal(2)).isEqualByComparingTo("11250000000000.0000");
                    assertThat(result.getObject(3, OffsetDateTime.class).getOffset()).isEqualTo(ZoneOffset.UTC);
                }
            }
        }
    }

    @Test
    void rejectsBrokenCorrectionLinksAndBreadthReconciliation() throws Exception {
        assertThatThrownBy(() -> insertAcceptedIngestion("VN30", "broken-link", UUID.randomUUID()))
                .isInstanceOf(SQLException.class);

        try (var connection = connection();
                var insert = connection.prepareStatement("""
                    insert into breadth_snapshot (
                      id, trading_date, as_of, calculated_at, universe_policy_version,
                      universe_revision_hash, advancing, declining, unchanged, eligible,
                      unclassified, data_status, reason_codes, source_summary,
                      calculation_version)
                    values (?, '2026-08-17', now(), now(), 'breadth-universe-v1', ?,
                      10, 5, 2, 20, 1, 'PARTIAL', '{}', '{}', 'breadth-v1')
                    """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, "0".repeat(64));
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    private static UUID insertAcceptedIngestion(String subject, String hashSeed) throws Exception {
        return insertAcceptedIngestion(subject, hashSeed, null);
    }

    private static UUID insertAcceptedIngestion(String subject, String hashSeed, UUID supersedesId)
            throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = connection();
                var insert = connection.prepareStatement("""
                    insert into ingestion_record (
                      id, source, dataset, subject_key, trading_date, observed_at,
                      ingested_at, payload_hash, status, supersedes_id)
                    values (?, 'FINVERA_FIXTURE', 'INDEX', ?, '2026-08-17',
                      '2026-08-17T03:00:00Z', '2026-08-17T03:00:01Z', ?, 'ACCEPTED', ?)
                    """)) {
            insert.setObject(1, id);
            insert.setString(2, subject);
            insert.setString(3, String.format("%064x", hashSeed.hashCode() & 0xffffffffL));
            insert.setObject(4, supersedesId);
            insert.executeUpdate();
        }
        return id;
    }

    private static java.sql.Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
