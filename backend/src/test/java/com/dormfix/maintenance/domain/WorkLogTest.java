package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkLogTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void aWorkLogCarriesItsWorkerActionAndOptionalVisit() {
        WorkLog log = new WorkLog(5L, 7L, 9L, WorkActionType.REPAIR, "replaced the trap", NOW);

        assertThat(log.getRequestId()).isEqualTo(5L);
        assertThat(log.getVisitId()).isEqualTo(7L);
        assertThat(log.getWorkerId()).isEqualTo(9L);
        assertThat(log.getActionType()).isEqualTo(WorkActionType.REPAIR);
        assertThat(log.getDescription()).isEqualTo("replaced the trap");
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void theVisitLinkIsOptional() {
        WorkLog log = new WorkLog(5L, null, 9L, WorkActionType.NOTE, "left a note", NOW);

        assertThat(log.getVisitId()).isNull();
    }

    @Test
    void aDescriptionIsRequired() {
        assertThatThrownBy(() -> new WorkLog(5L, null, 9L, WorkActionType.NOTE, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkLog(5L, null, 9L, WorkActionType.NOTE, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRequestWorkerActionAndTimeAreRequired() {
        assertThatThrownBy(() -> new WorkLog(null, null, 9L, WorkActionType.NOTE, "x", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkLog(5L, null, null, WorkActionType.NOTE, "x", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkLog(5L, null, 9L, null, "x", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkLog(5L, null, 9L, WorkActionType.NOTE, "x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void thereIsNoMutatorSoTheRowStaysAppendOnly() {
        assertThat(java.util.Arrays.stream(WorkLog.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set"))).isEmpty();
    }

    @Test
    void theFrozenActionTypesAreExactlyTheFiveValues() {
        assertThat(WorkActionType.values()).containsExactly(WorkActionType.INSPECTION,
                WorkActionType.REPAIR, WorkActionType.REPLACEMENT, WorkActionType.TEST,
                WorkActionType.NOTE);
    }
}
