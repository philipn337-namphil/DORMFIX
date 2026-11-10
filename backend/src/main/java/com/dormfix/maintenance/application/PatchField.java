package com.dormfix.maintenance.application;

/**
 * One optional PATCH field. {@code present} separates an omitted field, which must leave the stored
 * value alone, from an explicit null, which clears a nullable field.
 */
public record PatchField<T>(boolean present, T value) {
    public static <T> PatchField<T> absent() {
        return new PatchField<>(false, null);
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }

    public T orElse(T current) {
        return present ? value : current;
    }
}
