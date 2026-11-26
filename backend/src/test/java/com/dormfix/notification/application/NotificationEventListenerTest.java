package com.dormfix.notification.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dormfix.maintenance.application.event.MaintenanceEvent;
import org.junit.jupiter.api.Test;

class NotificationEventListenerTest {
    @Test
    void aRecorderFailureIsSwallowedSoTheCommittedCommandIsUnaffected() {
        NotificationRecorder recorder = mock(NotificationRecorder.class);
        when(recorder.record(any())).thenThrow(new IllegalStateException("db down"));
        NotificationEventListener listener = new NotificationEventListener(recorder);

        assertThatCode(() -> listener.on(new MaintenanceEvent.MaintenanceStarted(1L, "DF-1", 2L,
                3L))).doesNotThrowAnyException();
    }
}
