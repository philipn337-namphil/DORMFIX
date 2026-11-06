package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceAssignmentTest {
    private static final Instant ASSIGNED_AT = Instant.parse("2026-11-04T01:00:00Z");

    @Test
    void anewAssignmentIsActiveAndCarriesItsActors() {
        MaintenanceAssignment assignment = assignment();

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getUnassignedAt()).isNull();
        assertThat(assignment.getUnassignedById()).isNull();
        assertThat(assignment.getWorkerId()).isEqualTo(7L);
        assertThat(assignment.getAssignedById()).isEqualTo(9L);
        assertThat(assignment.getReason()).isEqualTo("initial");
    }

    @Test
    void endingAnAssignmentKeepsTheRowAndRecordsWhoEndedIt() {
        MaintenanceAssignment assignment = assignment();

        assignment.end(11L, Instant.parse("2026-11-04T02:00:00Z"));

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getUnassignedById()).isEqualTo(11L);
        assertThat(assignment.getUnassignedAt()).isEqualTo(Instant.parse("2026-11-04T02:00:00Z"));
        assertThat(assignment.getWorkerId()).isEqualTo(7L);
    }

    @Test
    void anEndedAssignmentCannotBeEndedAgain() {
        MaintenanceAssignment assignment = assignment();
        assignment.end(11L, Instant.parse("2026-11-04T02:00:00Z"));

        assertThatThrownBy(() -> assignment.end(12L, Instant.parse("2026-11-04T03:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAssignmentCannotEndBeforeItStarted() {
        MaintenanceAssignment assignment = assignment();

        assertThatThrownBy(() -> assignment.end(11L, ASSIGNED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private MaintenanceAssignment assignment() {
        return new MaintenanceAssignment(5L, 7L, 9L, "initial", ASSIGNED_AT);
    }
}
