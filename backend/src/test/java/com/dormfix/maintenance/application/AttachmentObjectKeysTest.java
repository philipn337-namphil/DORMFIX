package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The key prefix is what stands in for a pending-upload table, so belongsTo has to be exact: a key
 * issued for another request or another uploader, or one with anything but a bare UUID after the
 * prefix, must not pass.
 */
class AttachmentObjectKeysTest {
    private static final String UUID = "3f2b8c1e-9a4d-4c7b-8e21-0f5a6b7c8d9e";

    @Test
    void aGeneratedKeyBelongsToItsRequestAndUploaderAndNoOneElse() {
        String key = AttachmentObjectKeys.newKey(12L, 34L);

        assertThat(key).startsWith("requests/12/attachments/34/");
        assertThat(AttachmentObjectKeys.belongsTo(key, 12L, 34L)).isTrue();
        assertThat(AttachmentObjectKeys.belongsTo(key, 13L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(key, 12L, 35L)).isFalse();
    }

    @Test
    void generatedKeysAreUniqueAndNeverContainAFileName() {
        String first = AttachmentObjectKeys.newKey(1L, 2L);
        String second = AttachmentObjectKeys.newKey(1L, 2L);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("requests/1/attachments/2/[0-9a-f-]{36}");
    }

    @Test
    void anIdThatIsOnlyAPrefixOfAnotherIdDoesNotMatch() {
        // Request 1 must not accept a key issued for request 12, nor uploader 3 one for 34.
        String key = "requests/12/attachments/34/" + UUID;

        assertThat(AttachmentObjectKeys.belongsTo(key, 1L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(key, 12L, 3L)).isFalse();
    }

    @Test
    void anythingOtherThanABareUuidAfterThePrefixIsRejected() {
        String prefix = "requests/12/attachments/34/";

        assertThat(AttachmentObjectKeys.belongsTo(prefix + UUID, 12L, 34L)).isTrue();
        assertThat(AttachmentObjectKeys.belongsTo(prefix, 12L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(prefix + UUID + "/extra", 12L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(prefix + "../../99/attachments/1/" + UUID, 12L,
                34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(prefix + "photo.png", 12L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(prefix + UUID.toUpperCase(), 12L, 34L))
                .isFalse();
        assertThat(AttachmentObjectKeys.belongsTo(prefix + UUID + "\n", 12L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo("/" + prefix + UUID, 12L, 34L)).isFalse();
    }

    @Test
    void nullsNeverBelong() {
        assertThat(AttachmentObjectKeys.belongsTo(null, 12L, 34L)).isFalse();
        assertThat(AttachmentObjectKeys.belongsTo("requests/12/attachments/34/" + UUID, null, 34L))
                .isFalse();
        assertThat(AttachmentObjectKeys.belongsTo("requests/12/attachments/34/" + UUID, 12L, null))
                .isFalse();
    }
}
