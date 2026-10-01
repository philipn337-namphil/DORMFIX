package com.dormfix.identity.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(
        name = "admin_dormitory_scopes",
        indexes = @Index(
                name = "ix_admin_dormitory_scopes_dormitory_user",
                columnList = "dormitory_id, user_id"))
public class AdminDormitoryScope {
    @EmbeddedId
    private AdminDormitoryScopeId id;

    protected AdminDormitoryScope() {
    }

    public AdminDormitoryScope(Long userId, Long dormitoryId) {
        this.id = new AdminDormitoryScopeId(
                Objects.requireNonNull(userId), Objects.requireNonNull(dormitoryId));
    }

    public AdminDormitoryScopeId getId() {
        return id;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public Long getDormitoryId() {
        return id.getDormitoryId();
    }
}
