package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryMetadataTest {
    @Test
    void aReasonIsSerializedSoQuotesAndBackslashesCannotExtendTheObject() {
        String reason = "part \"A\" \\ late\",\"commentId\":7";

        String json = HistoryMetadata.lifecycle(reason);
        HistoryMetadata.Projection read = HistoryMetadata.read(json);

        assertThat(read.reason()).isEqualTo(reason);
        assertThat(read.commentId()).isNull();
    }

    @Test
    void noReasonWritesNoMetadataAndDuplicateKeepsItsId() {
        assertThat(HistoryMetadata.lifecycle(null)).isNull();
        assertThat(HistoryMetadata.duplicate(5L, null)).isEqualTo("{\"duplicateOfId\":5}");
        HistoryMetadata.Projection read = HistoryMetadata.read(
                HistoryMetadata.duplicate(5L, "same"));
        assertThat(read.duplicateOfId()).isEqualTo(5L);
        assertThat(read.reason()).isEqualTo("same");
    }

    @Test
    void theFormatsAlreadyStoredByOtherCommandsAreProjected() {
        assertThat(HistoryMetadata.read("{\"workerId\":3,\"previousWorkerId\":2}"))
                .extracting("workerId", "previousWorkerId").containsExactly(3L, 2L);
        assertThat(HistoryMetadata.read("{\"visitId\":9}").visitId()).isEqualTo(9L);
        assertThat(HistoryMetadata.read("{\"commentId\":4}").commentId()).isEqualTo(4L);
        assertThat(HistoryMetadata.read("{\"fields\":[\"title\",\"description\"]}")
                .changedFields()).isEqualTo(List.of("title", "description"));
    }

    @Test
    void absentMetadataProjectsToNullsAndAnUnreadableRowIsADefect() {
        HistoryMetadata.Projection empty = HistoryMetadata.read(null);
        assertThat(empty.reason()).isNull();
        assertThat(empty.changedFields()).isNull();
        assertThatThrownBy(() -> HistoryMetadata.read("{not json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
