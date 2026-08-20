package com.minhnb.finvera_be.research.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression test for a contract drift found in review: the record component is named
 * {@code finalResult} (Java reserves {@code final}), but public-api.openapi.yaml names the
 * wire field {@code final}. Without {@code @JsonProperty("final")} on the component, Jackson
 * would silently serialize the wrong key and any client built against the published contract
 * (not just this repo's own frontend) would fail to read the final SSE event.
 */
class AskStreamEventTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void finalEventSerializesUnderTheContractsFinalKeyNotFinalResult() {
        AskFinalResult result = new AskFinalResult("Câu trả lời.", List.of(), false);
        AskStreamEvent event = AskStreamEvent.finalResult(result);

        JsonNode node = JSON.readTree(JSON.writeValueAsString(event));

        assertThat(node.has("final")).isTrue();
        assertThat(node.has("finalResult")).isFalse();
        assertThat(node.get("type").asString()).isEqualTo("final");
        assertThat(node.get("final").get("answer").asString()).isEqualTo("Câu trả lời.");
    }

    @Test
    void deltaEventOmitsTheFinalKeyEntirely() {
        AskStreamEvent event = AskStreamEvent.delta("một phần câu trả lời");

        JsonNode node = JSON.readTree(JSON.writeValueAsString(event));

        assertThat(node.get("type").asString()).isEqualTo("delta");
        assertThat(node.get("textDelta").asString()).isEqualTo("một phần câu trả lời");
        assertThat(node.has("final")).isFalse();
    }
}
