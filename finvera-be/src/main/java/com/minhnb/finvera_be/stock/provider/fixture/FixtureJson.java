package com.minhnb.finvera_be.stock.provider.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared JSON-tree reading helpers for the stock fixture provider adapters. */
final class FixtureJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private FixtureJson() {
    }

    static JsonNode read(String classpathLocation) {
        try (InputStream input = new ClassPathResource(classpathLocation).getInputStream()) {
            return MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read stock fixture: " + classpathLocation, exception);
        }
    }

    static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.stringValue().isBlank()) {
            throw new IllegalStateException("Missing normalized fixture field: " + field);
        }
        return value.stringValue();
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.stringValue();
    }

    static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : new BigDecimal(value.stringValue());
    }

    static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isTextual() ? Long.valueOf(value.stringValue()) : value.longValue();
    }

    static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.intValue();
    }

    static List<String> textList(JsonNode node) {
        return stream(node).map(JsonNode::stringValue).toList();
    }

    static Stream<JsonNode> stream(JsonNode array) {
        if (!array.isArray()) {
            return Stream.empty();
        }
        return StreamSupport.stream(array.spliterator(), false);
    }
}
