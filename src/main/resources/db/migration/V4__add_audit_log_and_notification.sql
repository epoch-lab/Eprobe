CREATE TABLE `audit_log`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `module`         VARCHAR(32)  NOT NULL,
    `action`         VARCHAR(100) NOT NULL,
    `severity`       VARCHAR(32)  NOT NULL,
    `actor_user_id`  BIGINT NULL,
    `actor_username` VARCHAR(100) NULL,
    `actor_role`     VARCHAR(32) NULL,
    `target_type`    VARCHAR(100) NULL,
    `target_id`      BIGINT NULL,
    `success`        BIT(1)       NOT NULL,
    `summary`        VARCHAR(255) NOT NULL,
    `detail_json`    TEXT NULL,
    `request_id`     VARCHAR(64) NULL,
    `request_path`   VARCHAR(255) NULL,
    `client_ip`      VARCHAR(64) NULL,
    `created_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_audit_log` PRIMARY KEY (`id`),
    CONSTRAINT `fk_audit_log_actor_user_id` FOREIGN KEY (`actor_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `recipient_user_id` BIGINT       NOT NULL,
    `type`              VARCHAR(64)  NOT NULL,
    `title`             VARCHAR(120) NOT NULL,
    `content`           TEXT         NOT NULL,
    `biz_key`           VARCHAR(160) NOT NULL,
    `related_type`      VARCHAR(100) NULL,
    `related_id`        BIGINT NULL,
    `sender_user_id`    BIGINT NULL,
    `read_at`           DATETIME(6) NULL,
    `created_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_notification` PRIMARY KEY (`id`),
    CONSTRAINT `uk_notification_recipient_biz_key` UNIQUE (`recipient_user_id`, `biz_key`),
    CONSTRAINT `fk_notification_recipient_user_id` FOREIGN KEY (`recipient_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_notification_sender_user_id` FOREIGN KEY (`sender_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_audit_log_module_created_at` ON `audit_log` (`module`, `created_at`);
CREATE INDEX `idx_audit_log_actor_user_id_created_at` ON `audit_log` (`actor_user_id`, `created_at`);
CREATE INDEX `idx_audit_log_severity_created_at` ON `audit_log` (`severity`, `created_at`);
CREATE INDEX `idx_notification_recipient_created_at` ON `notification` (`recipient_user_id`, `created_at`);
CREATE INDEX `idx_notification_recipient_read_at` ON `notification` (`recipient_user_id`, `read_at`);
