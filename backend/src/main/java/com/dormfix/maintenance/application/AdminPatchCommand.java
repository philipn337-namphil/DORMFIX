package com.dormfix.maintenance.application;

/** Administrator-owned fields only. It has no entry consent, title, description or space. */
public record AdminPatchCommand(Long requestId, Long version, PatchField<Long> categoryId,
        PatchField<String> priority, PatchField<Long> facilityId) { }
