package com.minhnb.finvera_be.research.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ResearchSchemaMigrationTests {

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

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void createsAllFourResearchTables() throws Exception {
        try (var connection = connection();
                var tables = connection.prepareStatement("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name IN
                          ('research_document', 'news_article', 'news_article_symbol', 'research_chunk')
                        """);
                var result = tables.executeQuery()) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    void enforcesIdempotencyKeyUniquePerOwnerOnResearchDocument() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "key-doc-1";

        try (var connection = connection()) {
            insertDocument(connection, UUID.randomUUID(), ownerId, "Doc 1", idempotencyKey);

            assertThatThrownBy(() -> insertDocument(connection, UUID.randomUUID(), ownerId, "Doc 2", idempotencyKey))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesIdempotencyKeyUniquePerOwnerOnNewsArticle() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "key-news-1";

        try (var connection = connection()) {
            insertNewsArticle(connection, UUID.randomUUID(), ownerId, "News 1", idempotencyKey);

            assertThatThrownBy(() -> insertNewsArticle(connection, UUID.randomUUID(), ownerId, "News 2", idempotencyKey))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesResearchDocumentQuarterRangeAndFailureReasonCheck() throws Exception {
        UUID ownerId = UUID.randomUUID();

        try (var connection = connection()) {
            // Invalid quarter (5) must fail check
            assertThatThrownBy(() -> insertDocumentRaw(connection, UUID.randomUUID(), ownerId, null, "Doc Title",
                    "QUARTERLY_REPORT", 2026, (short) 5, "Source", Date.valueOf(LocalDate.now()), null, null, null, null,
                    "READY", null, Timestamp.from(Instant.now()), null, "key-quarter-invalid"))
                    .isInstanceOf(SQLException.class);

            // Ingestion status FAILED without reason must fail check
            assertThatThrownBy(() -> insertDocumentRaw(connection, UUID.randomUUID(), ownerId, null, "Doc Title",
                    "ANNUAL_REPORT", 2026, null, "Source", Date.valueOf(LocalDate.now()), null, null, null, null,
                    "FAILED", null, Timestamp.from(Instant.now()), null, "key-failed-no-reason"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesNewsArticleClassificationApplicabilityChecks() throws Exception {
        UUID ownerId = UUID.randomUUID();

        try (var connection = connection()) {
            // category DEFINED but null category value must fail check
            assertThatThrownBy(() -> insertNewsArticleRaw(connection, UUID.randomUUID(), ownerId, "News Title", "Source",
                    Timestamp.from(Instant.now()), "Body text", null, null, "DEFINED", null, "MISSING", null, "MISSING",
                    null, "READY", null, Timestamp.from(Instant.now()), null, "key-applicability-invalid"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesResearchChunkExactlyOneParentCheck() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID newsId = UUID.randomUUID();

        try (var connection = connection()) {
            insertDocument(connection, docId, ownerId, "Parent Doc", "key-doc-parent");
            insertNewsArticle(connection, newsId, ownerId, "Parent News", "key-news-parent");

            // Chunk with BOTH parents must fail check
            assertThatThrownBy(() -> insertChunkRaw(connection, UUID.randomUUID(), ownerId, "DOCUMENT", docId, newsId,
                    (short) 0, (short) 1, null, "chunk text", "hash123", UUID.randomUUID(), "gemini-embedding-v1",
                    Timestamp.from(Instant.now())))
                    .isInstanceOf(SQLException.class);

            // Chunk with NEITHER parent must fail check
            assertThatThrownBy(() -> insertChunkRaw(connection, UUID.randomUUID(), ownerId, "DOCUMENT", null, null,
                    (short) 0, (short) 1, null, "chunk text", "hash123", UUID.randomUUID(), "gemini-embedding-v1",
                    Timestamp.from(Instant.now())))
                    .isInstanceOf(SQLException.class);

            // Chunk DOCUMENT with missing page_number must fail check
            assertThatThrownBy(() -> insertChunkRaw(connection, UUID.randomUUID(), ownerId, "DOCUMENT", docId, null,
                    (short) 0, null, null, "chunk text", "hash123", UUID.randomUUID(), "gemini-embedding-v1",
                    Timestamp.from(Instant.now())))
                    .isInstanceOf(SQLException.class);

            // Chunk NEWS_ARTICLE with missing paragraph_index must fail check
            assertThatThrownBy(() -> insertChunkRaw(connection, UUID.randomUUID(), ownerId, "NEWS_ARTICLE", null, newsId,
                    (short) 0, null, null, "chunk text", "hash123", UUID.randomUUID(), "gemini-embedding-v1",
                    Timestamp.from(Instant.now())))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void enforcesCascadeDeleteFromDocumentToChunks() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        try (var connection = connection()) {
            insertDocument(connection, docId, ownerId, "To Delete", "key-doc-delete");
            insertChunkRaw(connection, chunkId, ownerId, "DOCUMENT", docId, null, (short) 0, (short) 1, null,
                    "chunk text", "hash123", UUID.randomUUID(), "gemini-embedding-v1", Timestamp.from(Instant.now()));

            // Delete document
            try (var ps = connection.prepareStatement("DELETE FROM research_document WHERE id = ?")) {
                ps.setObject(1, docId);
                ps.executeUpdate();
            }

            // Verify chunk is cascaded
            try (var ps = connection.prepareStatement("SELECT count(*) FROM research_chunk WHERE id = ?")) {
                ps.setObject(1, chunkId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }

    private void insertDocument(Connection conn, UUID id, UUID ownerId, String title, String idempotencyKey)
            throws SQLException {
        insertDocumentRaw(conn, id, ownerId, null, title, "ANNUAL_REPORT", 2026, null, "Source",
                Date.valueOf(LocalDate.now()), null, null, null, "Extracted text", "READY", null,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), idempotencyKey);
    }

    private void insertDocumentRaw(Connection conn, UUID id, UUID ownerId, UUID symbolId, String title,
            String documentType, int year, Short quarter, String source, Date publicationDate,
            String originalFilename, byte[] originalContent, String contentMimeType, String extractedText,
            String ingestionStatus, String failureReason, Timestamp submittedAt, Timestamp processedAt,
            String idempotencyKey) throws SQLException {
        String sql = """
                INSERT INTO research_document (
                    id, owner_id, symbol_id, title, document_type, year, quarter, source, publication_date,
                    original_filename, original_content, content_mime_type, extracted_text, ingestion_status,
                    ingestion_failure_reason, submitted_at, processed_at, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setObject(3, symbolId);
            ps.setString(4, title);
            ps.setString(5, documentType);
            ps.setInt(6, year);
            if (quarter == null) ps.setNull(7, java.sql.Types.SMALLINT); else ps.setShort(7, quarter);
            ps.setString(8, source);
            ps.setDate(9, publicationDate);
            ps.setString(10, originalFilename);
            ps.setBytes(11, originalContent);
            ps.setString(12, contentMimeType);
            ps.setString(13, extractedText);
            ps.setString(14, ingestionStatus);
            ps.setString(15, failureReason);
            ps.setTimestamp(16, submittedAt);
            ps.setTimestamp(17, processedAt);
            ps.setString(18, idempotencyKey);
            ps.executeUpdate();
        }
    }

    private void insertNewsArticle(Connection conn, UUID id, UUID ownerId, String title, String idempotencyKey)
            throws SQLException {
        insertNewsArticleRaw(conn, id, ownerId, title, "Source", Timestamp.from(Instant.now()), "News Body", null,
                "COMPANY", "DEFINED", "POSITIVE", "DEFINED", "HIGH", "DEFINED", "Finance", "READY", null,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), idempotencyKey);
    }

    private void insertNewsArticleRaw(Connection conn, UUID id, UUID ownerId, String title, String source,
            Timestamp publishedAt, String body, String referenceUrl, String category, String categoryApplicability,
            String sentiment, String sentimentApplicability, String impactLevel, String impactApplicability,
            String sector, String ingestionStatus, String failureReason, Timestamp submittedAt,
            Timestamp processedAt, String idempotencyKey) throws SQLException {
        String sql = """
                INSERT INTO news_article (
                    id, owner_id, title, source, published_at, body, reference_url,
                    category, category_applicability, sentiment, sentiment_applicability,
                    impact_level, impact_applicability, sector, ingestion_status,
                    ingestion_failure_reason, submitted_at, processed_at, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, title);
            ps.setString(4, source);
            ps.setTimestamp(5, publishedAt);
            ps.setString(6, body);
            ps.setString(7, referenceUrl);
            ps.setString(8, category);
            ps.setString(9, categoryApplicability);
            ps.setString(10, sentiment);
            ps.setString(11, sentimentApplicability);
            ps.setString(12, impactLevel);
            ps.setString(13, impactApplicability);
            ps.setString(14, sector);
            ps.setString(15, ingestionStatus);
            ps.setString(16, failureReason);
            ps.setTimestamp(17, submittedAt);
            ps.setTimestamp(18, processedAt);
            ps.setString(19, idempotencyKey);
            ps.executeUpdate();
        }
    }

    private void insertChunkRaw(Connection conn, UUID id, UUID ownerId, String itemType, UUID researchDocumentId,
            UUID newsArticleId, short chunkIndex, Short pageNumber, Short paragraphIndex, String contentText,
            String contentHash, UUID vectorPointId, String embeddingVersion, Timestamp createdAt) throws SQLException {
        String sql = """
                INSERT INTO research_chunk (
                    id, owner_id, item_type, research_document_id, news_article_id, chunk_index,
                    page_number, paragraph_index, content_text, content_hash, vector_point_id,
                    embedding_version, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, itemType);
            ps.setObject(4, researchDocumentId);
            ps.setObject(5, newsArticleId);
            ps.setShort(6, chunkIndex);
            if (pageNumber == null) ps.setNull(7, java.sql.Types.SMALLINT); else ps.setShort(7, pageNumber);
            if (paragraphIndex == null) ps.setNull(8, java.sql.Types.SMALLINT); else ps.setShort(8, paragraphIndex);
            ps.setString(9, contentText);
            ps.setString(10, contentHash);
            ps.setObject(11, vectorPointId);
            ps.setString(12, embeddingVersion);
            ps.setTimestamp(13, createdAt);
            ps.executeUpdate();
        }
    }
}
