package com.dormfix.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NotificationTest {
    @Test
    void textIsTheFixedTemplateWithOnlyTheRequestNumber() {
        Notification value = new Notification(1L, 2L, NotificationType.REQUEST_ASSIGNED, "DF-2",
                Instant.parse("2026-11-27T00:00:00Z"));

        assertThat(value.getTitle()).isEqualTo("담당자가 배정되었습니다");
        assertThat(value.getMessage()).isEqualTo("DF-2 신고에 담당자가 배정되었습니다.");
    }

    @Test
    void everyTemplateFitsTheColumnsAndSubstitutesTheNumberOnce() {
        Arrays.stream(NotificationType.values()).forEach(type -> {
            assertThat(type.title().length()).isLessThanOrEqualTo(150);
            String message = type.message("DF-123456789");
            assertThat(message.length()).isLessThanOrEqualTo(500);
            assertThat(message).containsOnlyOnce("DF-123456789");
        });
    }

    @Test
    void markingReadIsIdempotentAndKeepsTheFirstTime() {
        Notification value = new Notification(1L, 2L, NotificationType.REQUEST_STARTED, "DF-2",
                Instant.parse("2026-11-27T00:00:00Z"));
        Instant first = Instant.parse("2026-11-27T01:00:00Z");

        value.markRead(first);
        value.markRead(Instant.parse("2026-11-27T02:00:00Z"));

        assertThat(value.getReadAt()).isEqualTo(first);
    }
}
