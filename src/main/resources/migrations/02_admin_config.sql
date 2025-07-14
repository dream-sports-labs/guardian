-- liquibase formatted sql
-- changeset guardian:2

CREATE TABLE admin_config
(
    tenant_id  CHAR(10) PRIMARY KEY,
    username   VARCHAR(50) NOT NULL,
    password   VARCHAR(50) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_tenant_admin_config FOREIGN KEY (tenant_id)
        REFERENCES tenant (id) ON DELETE CASCADE,

    UNIQUE KEY `idx_tenant_id` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci; 