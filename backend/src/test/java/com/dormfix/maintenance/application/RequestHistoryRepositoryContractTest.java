package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.maintenance.domain.RequestHistoryEventType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The resident history must not be able to reach COMMENT_DELETED rows through an argument. No port
 * method takes an event type or a flag, so the staff rows are reachable only by calling the staff
 * method by name.
 */
class RequestHistoryRepositoryContractTest {
    @Test
    void noPortMethodAcceptsAnEventTypeOrABooleanSwitch() {
        assertThat(Arrays.stream(RequestHistoryRepository.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .filter(type -> type == RequestHistoryEventType.class || type == boolean.class
                        || type == Boolean.class))
                .isEmpty();
    }

    @Test
    void theResidentListingAndCountTakeOnlyTheRequestAndPage() throws Exception {
        assertThat(RequestHistoryRepository.class.getMethod("findResidentPage", Long.class,
                int.class, int.class).getParameterTypes())
                .containsExactly(Long.class, int.class, int.class);
        assertThat(RequestHistoryRepository.class.getMethod("countResident", Long.class)
                .getParameterTypes()).containsExactly(Long.class);
    }
}
