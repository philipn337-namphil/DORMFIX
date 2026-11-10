package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.maintenance.application.AdminPatchCommand;
import com.dormfix.maintenance.application.InvalidMaintenanceQueryException;
import com.dormfix.maintenance.application.ResidentPatchCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The two field sets are the contract. These tests pin that they never intersect and that a field
 * from the other set, or one that is not metadata at all, cannot get through either parser.
 */
class MetadataPatchParserTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theTwoFieldSetsShareNothingButVersion() {
        Set<String> shared = new HashSet<>(MetadataPatchParser.RESIDENT_FIELDS);
        shared.retainAll(MetadataPatchParser.ADMINISTRATOR_FIELDS);

        assertThat(shared).containsExactly("version");
    }

    @Test
    void neitherSetContainsAFieldThatNoRoleMayPatch() {
        for (String forbidden : Set.of("status", "reporterId", "requestNumber", "spaceId",
                "duplicateOfId", "statusReason", "resolvedAt", "closedAt", "assigneeId",
                "workerId", "id", "createdAt", "updatedAt")) {
            assertThat(MetadataPatchParser.RESIDENT_FIELDS).doesNotContain(forbidden);
            assertThat(MetadataPatchParser.ADMINISTRATOR_FIELDS).doesNotContain(forbidden);
        }
    }

    @Test
    void aResidentBodyCannotCarryAdministratorFields() throws Exception {
        for (String field : Set.of("\"priority\":\"HIGH\"", "\"categoryId\":3",
                "\"facilityId\":7")) {
            assertInvalid(() -> MetadataPatchParser.resident(1L,
                    json("{" + field + ",\"version\":0}")));
        }
    }

    @Test
    void anAdministratorBodyCannotCarryResidentFields() throws Exception {
        for (String field : Set.of("\"entryPolicy\":\"ABSENT_ENTRY_ALLOWED\"",
                "\"contactBeforeEntry\":false", "\"title\":\"x\"", "\"description\":\"y\"",
                "\"preferredVisitStart\":null")) {
            assertInvalid(() -> MetadataPatchParser.administrator(1L,
                    json("{" + field + ",\"version\":0}")));
        }
    }

    @Test
    void neitherParserAcceptsStatusOrSpace() throws Exception {
        for (String field : Set.of("\"status\":\"CLOSED\"", "\"spaceId\":9",
                "\"reporterId\":5")) {
            assertInvalid(() -> MetadataPatchParser.resident(1L,
                    json("{" + field + ",\"version\":0}")));
            assertInvalid(() -> MetadataPatchParser.administrator(1L,
                    json("{" + field + ",\"version\":0}")));
        }
    }

    @Test
    void versionIsRequiredAndMustBeAnInteger() throws Exception {
        assertInvalid(() -> MetadataPatchParser.resident(1L, json("{\"title\":\"x\"}")));
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"title\":\"x\",\"version\":\"0\"}")));
        assertInvalid(() -> MetadataPatchParser.administrator(1L, json("{\"version\":null}")));
    }

    @Test
    void omittedFieldsAreAbsentAndSuppliedFieldsArePresent() throws Exception {
        ResidentPatchCommand command =
                MetadataPatchParser.resident(4L, json("{\"title\":\"New\",\"version\":3}"));

        assertThat(command.requestId()).isEqualTo(4L);
        assertThat(command.version()).isEqualTo(3L);
        assertThat(command.title().present()).isTrue();
        assertThat(command.title().value()).isEqualTo("New");
        assertThat(command.description().present()).isFalse();
        assertThat(command.preferredVisitStart().present()).isFalse();
    }

    @Test
    void anExplicitNullClearsOnlyTheNullableFields() throws Exception {
        ResidentPatchCommand resident = MetadataPatchParser.resident(1L,
                json("{\"preferredVisitStart\":null,\"preferredVisitEnd\":null,\"version\":0}"));
        assertThat(resident.preferredVisitStart().present()).isTrue();
        assertThat(resident.preferredVisitStart().value()).isNull();

        AdminPatchCommand admin =
                MetadataPatchParser.administrator(1L, json("{\"facilityId\":null,\"version\":0}"));
        assertThat(admin.facilityId().present()).isTrue();
        assertThat(admin.facilityId().value()).isNull();

        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"title\":null,\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"contactBeforeEntry\":null,\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.administrator(1L,
                json("{\"categoryId\":null,\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.administrator(1L,
                json("{\"priority\":null,\"version\":0}")));
    }

    @Test
    void textIsBoundedAndTimestampsMustParse() throws Exception {
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"title\":\"" + "x".repeat(151) + "\",\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"description\":\"" + "x".repeat(2001) + "\",\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"title\":\"   \",\"version\":0}")));
        assertInvalid(() -> MetadataPatchParser.resident(1L,
                json("{\"preferredVisitStart\":\"tomorrow\",\"version\":0}")));

        ResidentPatchCommand ok = MetadataPatchParser.resident(1L,
                json("{\"preferredVisitStart\":\"2026-11-11T10:00:00Z\",\"version\":0}"));
        assertThat(ok.preferredVisitStart().value())
                .isEqualTo(Instant.parse("2026-11-11T10:00:00Z"));
    }

    @Test
    void aNonObjectBodyIsRejected() throws Exception {
        assertInvalid(() -> MetadataPatchParser.resident(1L, json("[]")));
        assertInvalid(() -> MetadataPatchParser.administrator(1L, json("\"text\"")));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidMaintenanceQueryException.class);
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }
}
