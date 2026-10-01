CREATE TABLE admin_dormitory_scopes (
    user_id BIGINT NOT NULL,
    dormitory_id BIGINT NOT NULL,
    CONSTRAINT pk_admin_dormitory_scopes PRIMARY KEY (user_id, dormitory_id),
    CONSTRAINT fk_admin_dormitory_scopes_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_dormitory_scopes_dormitory
        FOREIGN KEY (dormitory_id) REFERENCES dormitory (id) ON DELETE RESTRICT
);

CREATE INDEX ix_admin_dormitory_scopes_dormitory_user
    ON admin_dormitory_scopes (dormitory_id, user_id);
