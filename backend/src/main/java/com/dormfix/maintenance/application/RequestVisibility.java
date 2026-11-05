package com.dormfix.maintenance.application;

import java.util.Set;

/**
 * Resolved read visibility for one caller. {@code allScopes} is SUPER_ADMIN. {@code resident}
 * matches requests the caller reported, {@code worker} matches requests the caller is or was
 * assigned to, and {@code dormitoryIds} are the administrator scopes. The set is never empty: a
 * caller with no administrator scope gets a sentinel that cannot match a real dormitory id, so the
 * native IN list stays valid.
 */
public record RequestVisibility(Long actorId, boolean allScopes, boolean resident, boolean worker,
        Set<Long> dormitoryIds) {
    public static final Long NO_DORMITORY = Long.MIN_VALUE;

    public RequestVisibility {
        dormitoryIds = dormitoryIds == null || dormitoryIds.isEmpty() ? Set.of(NO_DORMITORY)
                : Set.copyOf(dormitoryIds);
    }

    /** True when no rule can ever match, so the caller sees nothing at all. */
    public boolean deniesEverything() {
        return !allScopes && !resident && !worker && dormitoryIds.equals(Set.of(NO_DORMITORY));
    }
}
