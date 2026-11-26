package com.dormfix.maintenance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the RequestHistory {@code metadata} JSON. A lifecycle reason is free text typed
 * by a user, so it is serialized rather than concatenated: a quote or backslash in it must not be
 * able to break or extend the stored object. Reading projects only the known keys; the raw JSON is
 * never handed to the HTTP boundary.
 */
final class HistoryMetadata {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HistoryMetadata() { }

    /** {@code {"reason": ...}}, or {@code null} when the command carried no reason. */
    static String lifecycle(String reason) {
        return write(withReason(new LinkedHashMap<>(), reason));
    }

    /** {@code {"duplicateOfId": ..., "reason": ...}}; the reason key is absent when not supplied. */
    static String duplicate(Long duplicateOfId, String reason) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("duplicateOfId", duplicateOfId);
        return write(withReason(values, reason));
    }

    static Projection read(String json) {
        if (json == null || json.isBlank()) {
            return Projection.EMPTY;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            // Rows are written only by this application; an unreadable one is a defect, not input.
            throw new IllegalStateException("Unreadable request history metadata.", error);
        }
        return new Projection(text(node, "reason"), number(node, "workerId"),
                number(node, "previousWorkerId"), number(node, "visitId"),
                number(node, "duplicateOfId"), fields(node), number(node, "commentId"));
    }

    private static Map<String, Object> withReason(Map<String, Object> values, String reason) {
        if (reason != null) {
            values.put("reason", reason);
        }
        return values;
    }

    private static String write(Map<String, Object> values) {
        if (values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize request history metadata.", error);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || !value.canConvertToLong() ? null : value.asLong();
    }

    private static List<String> fields(JsonNode node) {
        JsonNode value = node.get("fields");
        if (value == null || !value.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>(value.size());
        value.forEach(item -> names.add(item.asText()));
        return List.copyOf(names);
    }

    record Projection(String reason, Long workerId, Long previousWorkerId, Long visitId,
            Long duplicateOfId, List<String> changedFields, Long commentId) {
        static final Projection EMPTY = new Projection(null, null, null, null, null, null, null);
    }
}
