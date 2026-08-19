package com.minhnb.finvera_be.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PortfolioSchemaMigrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void migrateFromEmpty() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsAllFourPortfolioAndWatchlistTables() throws Exception {
        try (var connection = connection();
                var tables = connection.prepareStatement("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name IN
                          ('portfolio', 'portfolio_transaction', 'watchlist', 'watchlist_item')
                        """);
                var result = tables.executeQuery()) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    void enforcesUniquePortfolioNamePerOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (var connection = connection()) {
            insertPortfolio(connection, UUID.randomUUID(), ownerId, "Growth Portfolio");

            assertThatThrownBy(() -> insertPortfolio(connection, UUID.randomUUID(), ownerId, "Growth Portfolio"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesUniqueWatchlistNamePerOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (var connection = connection()) {
            insertWatchlist(connection, UUID.randomUUID(), ownerId, "Tech Stocks");

            assertThatThrownBy(() -> insertWatchlist(connection, UUID.randomUUID(), ownerId, "Tech Stocks"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesCompositePrimaryKeyOnWatchlistItemPreventingDuplicates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID watchlistId = UUID.randomUUID();
        UUID instrumentId = insertInstrument("VNM");

        try (var connection = connection()) {
            insertWatchlist(connection, watchlistId, ownerId, "Dairy");
            insertWatchlistItem(connection, watchlistId, instrumentId);

            assertThatThrownBy(() -> insertWatchlistItem(connection, watchlistId, instrumentId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesBuyRequiresInstrumentQuantityAndPrice() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        try (var connection = connection()) {
            insertPortfolio(connection, portfolioId, ownerId, "Buy Check PF");

            // BUY without instrumentId must fail check
            assertThatThrownBy(() -> insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "key-1",
                    "BUY", null, 100.0, 50.0, 0.0, null, "VND", Instant.now(), Instant.now(), null, null))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesDepositRequiresAmount() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        try (var connection = connection()) {
            insertPortfolio(connection, portfolioId, ownerId, "Deposit Check PF");

            // DEPOSIT without amount must fail check
            assertThatThrownBy(() -> insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "key-2",
                    "DEPOSIT", null, null, null, 0.0, null, "VND", Instant.now(), Instant.now(), null, null))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesVoidRequiresTargetTransactionAndReason() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        try (var connection = connection()) {
            insertPortfolio(connection, portfolioId, ownerId, "Void Check PF");

            // VOID without target must fail check
            assertThatThrownBy(() -> insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "key-3",
                    "VOID", null, null, null, 0.0, null, "VND", Instant.now(), Instant.now(), null, null))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesUniqueVoidsTransactionIdAllowingAtMostOneVoidPerTarget() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        UUID depositId = UUID.randomUUID();

        try (var connection = connection()) {
            insertPortfolio(connection, portfolioId, ownerId, "Double Void PF");
            insertTransactionRaw(connection, depositId, portfolioId, "dep-1",
                    "DEPOSIT", null, null, null, 0.0, 10000000.0, "VND", Instant.now(), Instant.now(), null, null);

            // First VOID succeeds
            insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "void-1",
                    "VOID", null, null, null, 0.0, null, "VND", Instant.now(), Instant.now(), depositId, "Mistake");

            // Second VOID referencing the same deposit must fail unique index
            assertThatThrownBy(() -> insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "void-2",
                    "VOID", null, null, null, 0.0, null, "VND", Instant.now(), Instant.now(), depositId, "Second void"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesUniqueIdempotencyKeyPerPortfolio() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        try (var connection = connection()) {
            insertPortfolio(connection, portfolioId, ownerId, "Idempotency PF");

            insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "idem-key-123",
                    "DEPOSIT", null, null, null, 0.0, 5000000.0, "VND", Instant.now(), Instant.now(), null, null);

            assertThatThrownBy(() -> insertTransactionRaw(connection, UUID.randomUUID(), portfolioId, "idem-key-123",
                    "DEPOSIT", null, null, null, 0.0, 5000000.0, "VND", Instant.now(), Instant.now(), null, null))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insertPortfolio(Connection connection, UUID id, UUID ownerId, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO portfolio (id, owner_id, name, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, name);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private static void insertWatchlist(Connection connection, UUID id, UUID ownerId, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO watchlist (id, owner_id, name, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, name);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private static void insertWatchlistItem(Connection connection, UUID watchlistId, UUID instrumentId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO watchlist_item (watchlist_id, instrument_id, added_at)
                VALUES (?, ?, ?)
                """)) {
            ps.setObject(1, watchlistId);
            ps.setObject(2, instrumentId);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private static UUID insertInstrument(String symbol) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var connection = connection();
                var ps = connection.prepareStatement("""
                        INSERT INTO market_instrument (
                            id, venue, symbol, instrument_type, base_currency,
                            price_currency, lot_size, tick_size, status, listed_from
                        ) VALUES (?, 'HOSE', ?, 'EQUITY', 'VND', 'VND', 100, 10, 'ACTIVE', '2020-01-01')
                        """)) {
            ps.setObject(1, id);
            ps.setString(2, symbol);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertTransactionRaw(
            Connection connection,
            UUID id,
            UUID portfolioId,
            String idempotencyKey,
            String transactionType,
            UUID instrumentId,
            Double quantity,
            Double price,
            Double fee,
            Double amount,
            String currency,
            Instant executedAt,
            Instant entryAt,
            UUID voidsTransactionId,
            String voidReason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO portfolio_transaction (
                    id, portfolio_id, idempotency_key, transaction_type,
                    instrument_id, quantity, price, fee, amount, currency,
                    executed_at, entry_at, voids_transaction_id, void_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, portfolioId);
            ps.setString(3, idempotencyKey);
            ps.setString(4, transactionType);
            ps.setObject(5, instrumentId);
            if (quantity != null) ps.setDouble(6, quantity); else ps.setNull(6, java.sql.Types.NUMERIC);
            if (price != null) ps.setDouble(7, price); else ps.setNull(7, java.sql.Types.NUMERIC);
            if (fee != null) ps.setDouble(8, fee); else ps.setDouble(8, 0.0);
            if (amount != null) ps.setDouble(9, amount); else ps.setNull(9, java.sql.Types.NUMERIC);
            ps.setString(10, currency);
            ps.setTimestamp(11, Timestamp.from(executedAt));
            ps.setTimestamp(12, Timestamp.from(entryAt));
            ps.setObject(13, voidsTransactionId);
            ps.setString(14, voidReason);
            ps.executeUpdate();
        }
    }
}
