package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.maintenance.domain.CommentVisibility;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The resident listing must not be able to reach STAFF_ONLY rows through an argument. This pins the
 * shape of the port: no listing or count method takes a visibility or a flag, so the only way to
 * read internal remarks is to call the staff method by name.
 */
class CommentRepositoryContractTest {
    @Test
    void theResidentListingAndCountTakeNoVisibilityOrFlag() throws Exception {
        Method page = CommentRepository.class.getMethod("findPublicPage", Long.class, int.class,
                int.class);
        Method count = CommentRepository.class.getMethod("countPublic", Long.class);

        assertThat(page.getParameterTypes()).containsExactly(Long.class, int.class, int.class);
        assertThat(count.getParameterTypes()).containsExactly(Long.class);
    }

    @Test
    void noPortMethodAcceptsAVisibilityOrABooleanSwitch() {
        assertThat(Arrays.stream(CommentRepository.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .filter(type -> type == CommentVisibility.class || type == boolean.class
                        || type == Boolean.class))
                .isEmpty();
    }
}
