package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AnalystConversationPerformanceTests {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000029");
    private static final UUID TARGET=UUID.fromString("29000000-0000-0000-0000-000000000000");

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection c=connection(); var conversations=c.prepareStatement("""
                insert into analyst_conversation
                    (id,owner_id,title,title_source,created_at,updated_at,last_activity_at,next_sequence_no)
                select md5(('conversation-' || i)::text)::uuid, ?, 'Conversation ' || i, 'AUTO',
                    now(), now(), now() - i * interval '1 second', 1
                from generate_series(1,10000) i
                """)) {
            conversations.setObject(1, OWNER);
            conversations.executeUpdate();
        }
        try (Connection c=connection(); var conversation=c.prepareStatement("""
                insert into analyst_conversation
                    (id,owner_id,title,title_source,created_at,updated_at,last_activity_at,next_sequence_no)
                values (?,?,'Target','AUTO',now(),now(),now(),10001)
                """)) {
            conversation.setObject(1, TARGET); conversation.setObject(2, OWNER); conversation.executeUpdate();
        }
        try (Connection c=connection(); var exchanges=c.prepareStatement("""
                insert into analyst_conversation_exchange
                    (id,conversation_id,owner_id,sequence_no,client_request_id,question,status,answer,
                     response_schema_version,response_metadata,context_rule_version,context_included_count,
                     context_omitted_count,created_at,completed_at)
                select md5(('exchange-' || i)::text)::uuid, ?, ?, i,
                    md5(('request-' || i)::text)::uuid, 'Question ' || i, 'COMPLETED', 'Answer ' || i,
                    'analyst-conversation-answer-v1', '{}'::jsonb, 'context-window-v1', 0, 0, now(), now()
                from generate_series(1,10000) i
                """)) {
            exchanges.setObject(1, TARGET); exchanges.setObject(2, OWNER); exchanges.executeUpdate();
        }
        try (Connection c=connection(); var analyze=c.createStatement()) {
            analyze.execute("analyze analyst_conversation");
            analyze.execute("analyze analyst_conversation_exchange");
        }
    }

    @Test
    void indexedFirstPagesMeetLocalP95TargetAtTenThousandRows() throws Exception {
        List<Long> conversationReads=new ArrayList<>();
        List<Long> exchangeReads=new ArrayList<>();
        for (int i=0; i<20; i++) {
            conversationReads.add(timedConversationPage());
            exchangeReads.add(timedExchangePage());
        }
        long conversationP95=p95(conversationReads);
        long exchangeP95=p95(exchangeReads);
        System.out.printf("conversation_history_performance list_p95_ms=%d exchange_p95_ms=%d%n",
                conversationP95, exchangeP95);
        assertThat(conversationP95).isLessThanOrEqualTo(1_000L);
        assertThat(exchangeP95).isLessThanOrEqualTo(1_000L);
    }

    @Test
    void acceptedPersistenceOverheadMeetsLocalP95Target() throws Exception {
        List<Long> samples=new ArrayList<>();
        for (int i=1; i<=20; i++) {
            UUID conversation=UUID.randomUUID(); UUID exchange=UUID.randomUUID();
            long started=System.nanoTime();
            try (Connection c=connection()) {
                c.setAutoCommit(false);
                try (var pc=c.prepareStatement("insert into analyst_conversation(id,owner_id,title,title_source,created_at,updated_at,last_activity_at,next_sequence_no) values (?,?,'Accepted','AUTO',now(),now(),now(),2)");
                     var pe=c.prepareStatement("insert into analyst_conversation_exchange(id,conversation_id,owner_id,sequence_no,client_request_id,question,status,context_rule_version,context_included_count,context_omitted_count,created_at) values (?,?,?,1,?,'Question','PROCESSING','context-window-v1',0,0,now())")) {
                    pc.setObject(1, conversation); pc.setObject(2, OWNER); pc.executeUpdate();
                    pe.setObject(1, exchange); pe.setObject(2, conversation); pe.setObject(3, OWNER);
                    pe.setObject(4, UUID.randomUUID()); pe.executeUpdate();
                    c.commit();
                }
            }
            samples.add((System.nanoTime()-started)/1_000_000);
        }
        long acceptedP95=p95(samples);
        System.out.printf("conversation_history_performance accepted_p95_ms=%d%n", acceptedP95);
        assertThat(acceptedP95).isLessThanOrEqualTo(500L);
    }

    private static long timedConversationPage() throws Exception {
        long started=System.nanoTime();
        try (Connection c=connection(); var ps=c.prepareStatement("select id from analyst_conversation where owner_id=? order by last_activity_at desc,id desc limit 21")) {
            ps.setObject(1, OWNER); try (var rs=ps.executeQuery()) { int count=0; while(rs.next()) count++; assertThat(count).isEqualTo(21); }
        }
        return (System.nanoTime()-started)/1_000_000;
    }

    private static long timedExchangePage() throws Exception {
        long started=System.nanoTime();
        try (Connection c=connection(); var ps=c.prepareStatement("select id from analyst_conversation_exchange where conversation_id=? and owner_id=? order by sequence_no desc,id desc limit 51")) {
            ps.setObject(1, TARGET); ps.setObject(2, OWNER);
            try (var rs=ps.executeQuery()) { int count=0; while(rs.next()) count++; assertThat(count).isEqualTo(51); }
        }
        return (System.nanoTime()-started)/1_000_000;
    }

    private static long p95(List<Long> values) {
        Collections.sort(values);
        return values.get((int)Math.ceil(values.size()*0.95)-1);
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
