package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AnalystConversationMigrationTests {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsConversationTablesAndNullableAuditLink() throws Exception {
        try (Connection c=connection(); var ps=c.prepareStatement("""
                select count(*) from information_schema.tables where table_schema='public'
                and table_name in ('analyst_conversation','analyst_conversation_exchange')
                """); var rs=ps.executeQuery()) {
            rs.next(); assertThat(rs.getInt(1)).isEqualTo(2);
        }
        try (Connection c=connection(); var ps=c.prepareStatement("select count(*) from analyst_query where conversation_exchange_id is not null"); var rs=ps.executeQuery()) {
            rs.next(); assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test void enforcesOwnerScopedIdempotencyAndOneProcessingExchange() throws Exception {
        UUID owner=UUID.randomUUID(); UUID conversation=insertConversation(owner);
        UUID request=UUID.randomUUID(); insertProcessing(conversation, owner, 1, request);
        assertThatThrownBy(() -> insertProcessing(conversation, owner, 2, UUID.randomUUID())).isInstanceOf(SQLException.class);
        UUID otherConversation=insertConversation(owner);
        assertThatThrownBy(() -> insertProcessing(otherConversation, owner, 1, request)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertProcessing(otherConversation, UUID.randomUUID(), 1, UUID.randomUUID()))
                .isInstanceOf(SQLException.class);
    }

    @Test void deletingConversationCascadesExchangeLinkedQueryAndToolCallsOnly() throws Exception {
        UUID owner=UUID.randomUUID(); UUID conversation=insertConversation(owner); UUID exchange=insertCompleted(conversation, owner);
        UUID linkedQuery=insertQuery(owner, exchange); insertToolCall(linkedQuery);
        UUID legacyQuery=insertQuery(owner, null); insertToolCall(legacyQuery);
        try (Connection c=connection(); var ps=c.prepareStatement("delete from analyst_conversation where id=?")) {
            ps.setObject(1, conversation); ps.executeUpdate();
        }
        assertThat(count("analyst_conversation_exchange", exchange)).isZero();
        assertThat(count("analyst_query", linkedQuery)).isZero();
        assertThat(count("analyst_query", legacyQuery)).isOne();
    }

    private static UUID insertConversation(UUID owner) throws Exception {
        UUID id=UUID.randomUUID();
        try (Connection c=connection(); var ps=c.prepareStatement("insert into analyst_conversation(id,owner_id,title,title_source,created_at,updated_at,last_activity_at,next_sequence_no) values (?,?,?,'AUTO',now(),now(),now(),2)")) {
            ps.setObject(1,id); ps.setObject(2,owner); ps.setString(3,"Title"); ps.executeUpdate();
        } return id;
    }
    private static void insertProcessing(UUID conversation, UUID owner, long sequence, UUID request) throws Exception {
        try (Connection c=connection(); var ps=c.prepareStatement("insert into analyst_conversation_exchange(id,conversation_id,owner_id,sequence_no,client_request_id,question,status,context_rule_version,context_included_count,context_omitted_count,created_at) values (?,?,?,?,?,?,'PROCESSING','context-window-v1',0,0,now())")) {
            ps.setObject(1,UUID.randomUUID()); ps.setObject(2,conversation); ps.setObject(3,owner); ps.setLong(4,sequence); ps.setObject(5,request); ps.setString(6,"Question"); ps.executeUpdate();
        }
    }
    private static UUID insertCompleted(UUID conversation, UUID owner) throws Exception {
        UUID id=UUID.randomUUID();
        try (Connection c=connection(); var ps=c.prepareStatement("insert into analyst_conversation_exchange(id,conversation_id,owner_id,sequence_no,client_request_id,question,status,answer,response_schema_version,response_metadata,context_rule_version,context_included_count,context_omitted_count,created_at,completed_at) values (?,?,?,?,?,?,'COMPLETED','Answer','analyst-conversation-answer-v1','{}'::jsonb,'context-window-v1',0,0,now(),now())")) {
            ps.setObject(1,id); ps.setObject(2,conversation); ps.setObject(3,owner); ps.setLong(4,1); ps.setObject(5,UUID.randomUUID()); ps.setString(6,"Question"); ps.executeUpdate();
        } return id;
    }
    private static UUID insertQuery(UUID owner, UUID exchange) throws Exception {
        UUID id=UUID.randomUUID();
        try (Connection c=connection(); var ps=c.prepareStatement("insert into analyst_query(id,owner_id,request_type,question_preview,question_hash,outcome,tool_call_bound_reached,requested_at,completed_at,conversation_exchange_id) values (?,?,'ASK','q',?,'COMPLETED',false,now(),now(),?)")) {
            ps.setObject(1,id); ps.setObject(2,owner); ps.setString(3,"a".repeat(64)); ps.setObject(4,exchange); ps.executeUpdate();
        } return id;
    }
    private static void insertToolCall(UUID query) throws Exception {
        try (Connection c=connection(); var ps=c.prepareStatement("insert into analyst_tool_call(id,analyst_query_id,sequence_no,tool_name,arguments,status,latency_ms,called_at) values (?,?,1,'STOCK','{}'::jsonb,'SUCCEEDED',1,now())")) {
            ps.setObject(1,UUID.randomUUID()); ps.setObject(2,query); ps.executeUpdate();
        }
    }
    private static int count(String table, UUID id) throws Exception {
        try (Connection c=connection(); var ps=c.prepareStatement("select count(*) from " + table + " where id=?")) {
            ps.setObject(1,id); try (var rs=ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
    private static Connection connection() throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
}
