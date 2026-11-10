package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.AdminPatchCommand;
import com.dormfix.maintenance.application.InvalidMaintenanceQueryException;
import com.dormfix.maintenance.application.PatchField;
import com.dormfix.maintenance.application.ResidentPatchCommand;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Set;

/**
 * Turns a raw PATCH body into one of two allow-listed commands. The body is read as a tree rather
 * than bound to a record because a PATCH must tell an omitted field from an explicit null, and a
 * bound record cannot. Each field set is closed: a field from the other set, or any field that is
 * not metadata at all, is rejected as unknown. Nothing here reflects an arbitrary field map onto
 * the entity.
 */
final class MetadataPatchParser {
    static final Set<String> RESIDENT_FIELDS = Set.of("title", "description", "entryPolicy",
            "contactBeforeEntry", "preferredVisitStart", "preferredVisitEnd", "version");
    static final Set<String> ADMINISTRATOR_FIELDS =
            Set.of("categoryId", "priority", "facilityId", "version");

    private static final int MAX_TITLE = 150;
    private static final int MAX_DESCRIPTION = 2000;
    private static final int MAX_ENUM_NAME = 40;

    private MetadataPatchParser() {
    }

    static ResidentPatchCommand resident(Long requestId, JsonNode body) {
        requireOnly(body, RESIDENT_FIELDS);
        return new ResidentPatchCommand(requestId, version(body),
                text(body, "title", MAX_TITLE), text(body, "description", MAX_DESCRIPTION),
                text(body, "entryPolicy", MAX_ENUM_NAME), bool(body, "contactBeforeEntry"),
                nullableInstant(body, "preferredVisitStart"),
                nullableInstant(body, "preferredVisitEnd"));
    }

    static AdminPatchCommand administrator(Long requestId, JsonNode body) {
        requireOnly(body, ADMINISTRATOR_FIELDS);
        return new AdminPatchCommand(requestId, version(body), id(body, "categoryId", false),
                text(body, "priority", MAX_ENUM_NAME), id(body, "facilityId", true));
    }

    private static void requireOnly(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw invalid("The request body must be a JSON object.");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw invalid("The request contains a field that cannot be changed.");
            }
        }
    }

    private static Long version(JsonNode body) {
        JsonNode node = body.get("version");
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid("version is required.");
        }
        return node.longValue();
    }

    private static PatchField<String> text(JsonNode body, String name, int maxLength) {
        if (!body.has(name)) {
            return PatchField.absent();
        }
        JsonNode node = body.get(name);
        if (!node.isTextual() || node.textValue().isBlank()
                || node.textValue().length() > maxLength) {
            throw invalid(name + " is invalid.");
        }
        return PatchField.of(node.textValue());
    }

    private static PatchField<Boolean> bool(JsonNode body, String name) {
        if (!body.has(name)) {
            return PatchField.absent();
        }
        JsonNode node = body.get(name);
        if (!node.isBoolean()) {
            throw invalid(name + " is invalid.");
        }
        return PatchField.of(node.booleanValue());
    }

    /** An explicit null clears the stored timestamp. */
    private static PatchField<Instant> nullableInstant(JsonNode body, String name) {
        if (!body.has(name)) {
            return PatchField.absent();
        }
        JsonNode node = body.get(name);
        if (node.isNull()) {
            return PatchField.of(null);
        }
        if (!node.isTextual()) {
            throw invalid(name + " is invalid.");
        }
        try {
            return PatchField.of(Instant.parse(node.textValue()));
        } catch (DateTimeParseException error) {
            throw invalid(name + " is invalid.");
        }
    }

    private static PatchField<Long> id(JsonNode body, String name, boolean nullable) {
        if (!body.has(name)) {
            return PatchField.absent();
        }
        JsonNode node = body.get(name);
        if (node.isNull() && nullable) {
            return PatchField.of(null);
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid(name + " is invalid.");
        }
        return PatchField.of(node.longValue());
    }

    private static InvalidMaintenanceQueryException invalid(String message) {
        return new InvalidMaintenanceQueryException(message);
    }
}
