package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.maintenance.application.VisitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The resident projection is the piece ADR-023 flags as most worth guarding: the staff note must
 * not reach a resident reader, and it must be absent from the payload rather than present as null.
 */
class VisitProjectionTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void theResidentProjectionOmitsTheNoteKeyEntirely() throws Exception {
        String json = mapper.writeValueAsString(VisitResponse.forResident(result()));

        assertThat(json).doesNotContain("note").doesNotContain("staff only remark");
        assertThat(mapper.readTree(json).has("note")).isFalse();
    }

    @Test
    void theResidentProjectionStillCarriesTheScheduleAndTheirOwnConsentSnapshot() throws Exception {
        var node = mapper.readTree(mapper.writeValueAsString(VisitResponse.forResident(result())));

        assertThat(node.get("scheduledStart").asText()).isNotEmpty();
        assertThat(node.get("scheduledEnd").asText()).isNotEmpty();
        assertThat(node.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(node.get("entryPolicySnapshot").asText())
                .isEqualTo("RESIDENT_PRESENT_REQUIRED");
        assertThat(node.get("contactBeforeEntry").asBoolean()).isTrue();
    }

    @Test
    void theStaffProjectionCarriesTheNote() throws Exception {
        var node = mapper.readTree(mapper.writeValueAsString(VisitResponse.forStaff(result())));

        assertThat(node.get("note").asText()).isEqualTo("staff only remark");
    }

    @Test
    void aResidentOnlyReaderGetsTheResidentProjectionAndAnyStaffRoleGetsTheNote() throws Exception {
        assertThat(body(Set.of("RESIDENT"))).doesNotContain("staff only remark");
        assertThat(body(Set.of("WORKER"))).contains("staff only remark");
        assertThat(body(Set.of("ADMIN"))).contains("staff only remark");
        assertThat(body(Set.of("SUPER_ADMIN"))).contains("staff only remark");
        // A caller who is both is staff for this purpose.
        assertThat(body(Set.of("RESIDENT", "WORKER"))).contains("staff only remark");
    }

    @Test
    void thereIsNoGeneralFactoryThatCouldLeakTheNoteByDefault() {
        assertThat(java.util.Arrays.stream(VisitResponse.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.equals("from"))).isEmpty();
    }

    private String body(Set<String> roles) throws Exception {
        return mapper.writeValueAsString(VisitListResponse.from(List.of(result()), roles));
    }

    private VisitResult result() {
        return new VisitResult(7L, 5L, 9L, 11L, Instant.parse("2026-11-07T10:00:00Z"),
                Instant.parse("2026-11-07T11:00:00Z"), null, null, "SCHEDULED",
                "RESIDENT_PRESENT_REQUIRED", true, "staff only remark",
                Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T09:00:00Z"));
    }
}
