package com.dormfix.maintenance.application;

import java.time.Instant;

/** Resident-owned fields only. It has no priority, category, facility, status or space. */
public record ResidentPatchCommand(Long requestId, Long version, PatchField<String> title,
        PatchField<String> description, PatchField<String> entryPolicy,
        PatchField<Boolean> contactBeforeEntry, PatchField<Instant> preferredVisitStart,
        PatchField<Instant> preferredVisitEnd) { }
