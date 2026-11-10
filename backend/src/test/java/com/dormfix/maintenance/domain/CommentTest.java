package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CommentTest {
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    void aCommentCarriesItsAuthorVisibilityAndContent() {
        Comment comment = new Comment(5L, 9L, "on my way", CommentVisibility.PUBLIC, NOW);

        assertThat(comment.getRequestId()).isEqualTo(5L);
        assertThat(comment.getAuthorId()).isEqualTo(9L);
        assertThat(comment.getContent()).isEqualTo("on my way");
        assertThat(comment.isPublic()).isTrue();
        assertThat(comment.isDeleted()).isFalse();
    }

    @Test
    void contentAndVisibilityAreRequired() {
        assertThatThrownBy(() -> new Comment(5L, 9L, " ", CommentVisibility.PUBLIC, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comment(5L, 9L, null, CommentVisibility.PUBLIC, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comment(5L, 9L, "x", null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void softDeleteKeepsTheRowAndHappensOnce() {
        Comment comment = new Comment(5L, 9L, "x", CommentVisibility.STAFF_ONLY, NOW);

        comment.softDelete(NOW.plusSeconds(60));

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(comment.getContent()).isEqualTo("x");
        assertThatThrownBy(() -> comment.softDelete(NOW.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCommentCannotBeDeletedBeforeItWasCreated() {
        Comment comment = new Comment(5L, 9L, "x", CommentVisibility.PUBLIC, NOW);

        assertThatThrownBy(() -> comment.softDelete(NOW.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thereIsNoEditPath() {
        assertThat(Arrays.stream(Comment.class.getMethods()).map(Method::getName)
                .filter(name -> name.startsWith("set") || name.startsWith("edit")
                        || name.startsWith("change"))).isEmpty();
    }
}
