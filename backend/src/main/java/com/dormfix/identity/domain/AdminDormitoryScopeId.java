package com.dormfix.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AdminDormitoryScopeId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dormitory_id", nullable = false)
    private Long dormitoryId;

    protected AdminDormitoryScopeId() {
    }

    public AdminDormitoryScopeId(Long userId, Long dormitoryId) {
        this.userId = Objects.requireNonNull(userId);
        this.dormitoryId = Objects.requireNonNull(dormitoryId);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDormitoryId() {
        return dormitoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AdminDormitoryScopeId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(dormitoryId, that.dormitoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, dormitoryId);
    }
}
