CREATE TABLE `stored_file`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `purpose`            VARCHAR(64)  NOT NULL,
    `original_file_name` VARCHAR(255) NOT NULL,
    `content_type`       VARCHAR(255) NULL,
    `size_bytes`         BIGINT       NOT NULL,
    `storage_path`       VARCHAR(512) NOT NULL,
    `uploader_user_id`   BIGINT       NOT NULL,
    `binding_type`       VARCHAR(64) NULL,
    `binding_id`         BIGINT NULL,
    `created_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_stored_file` PRIMARY KEY (`id`),
    CONSTRAINT `fk_stored_file_uploader_user_id` FOREIGN KEY (`uploader_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `file_upload_session`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `purpose`            VARCHAR(64)  NOT NULL,
    `original_file_name` VARCHAR(255) NOT NULL,
    `content_type`       VARCHAR(255) NULL,
    `total_size`         BIGINT       NOT NULL,
    `chunk_size`         BIGINT       NOT NULL,
    `received_size`      BIGINT       NOT NULL DEFAULT 0,
    `next_chunk_index`   INT          NOT NULL DEFAULT 0,
    `temp_storage_path`  VARCHAR(512) NOT NULL,
    `uploader_user_id`   BIGINT       NOT NULL,
    `created_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_file_upload_session` PRIMARY KEY (`id`),
    CONSTRAINT `fk_file_upload_session_uploader_user_id` FOREIGN KEY (`uploader_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recruitment_task`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `group_id`           BIGINT       NOT NULL,
    `title`              VARCHAR(100) NOT NULL,
    `content_markdown`   TEXT NULL,
    `attachment_file_id` BIGINT NULL,
    `max_score`          INT          NOT NULL,
    `deadline_at`        DATETIME(6) NOT NULL,
    `publisher_user_id`  BIGINT       NOT NULL,
    `created_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_recruitment_task` PRIMARY KEY (`id`),
    CONSTRAINT `fk_recruitment_task_group_id` FOREIGN KEY (`group_id`) REFERENCES `recruitment_group` (`id`),
    CONSTRAINT `fk_recruitment_task_attachment_file_id` FOREIGN KEY (`attachment_file_id`) REFERENCES `stored_file` (`id`),
    CONSTRAINT `fk_recruitment_task_publisher_user_id` FOREIGN KEY (`publisher_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_submission`
(
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
    `task_id`            BIGINT      NOT NULL,
    `user_id`            BIGINT      NOT NULL,
    `status`             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `content_markdown`   TEXT NULL,
    `attachment_file_id` BIGINT NULL,
    `submitted_at`       DATETIME(6) NULL,
    `reviewer_user_id`   BIGINT NULL,
    `score`              INT NULL,
    `review_comment`     TEXT NULL,
    `reviewed_at`        DATETIME(6) NULL,
    `created_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_task_submission` PRIMARY KEY (`id`),
    CONSTRAINT `uk_task_submission_task_user` UNIQUE (`task_id`, `user_id`),
    CONSTRAINT `fk_task_submission_task_id` FOREIGN KEY (`task_id`) REFERENCES `recruitment_task` (`id`),
    CONSTRAINT `fk_task_submission_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_task_submission_attachment_file_id` FOREIGN KEY (`attachment_file_id`) REFERENCES `stored_file` (`id`),
    CONSTRAINT `fk_task_submission_reviewer_user_id` FOREIGN KEY (`reviewer_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `announcement`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `title`             VARCHAR(100) NOT NULL,
    `content_markdown`  TEXT         NOT NULL,
    `scope`             VARCHAR(32)  NOT NULL,
    `group_id`          BIGINT NULL,
    `publisher_user_id` BIGINT       NOT NULL,
    `created_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_announcement` PRIMARY KEY (`id`),
    CONSTRAINT `fk_announcement_group_id` FOREIGN KEY (`group_id`) REFERENCES `recruitment_group` (`id`),
    CONSTRAINT `fk_announcement_publisher_user_id` FOREIGN KEY (`publisher_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `learning_material`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `group_id`           BIGINT       NOT NULL,
    `title`              VARCHAR(100) NOT NULL,
    `content_markdown`   TEXT NULL,
    `attachment_file_id` BIGINT NULL,
    `publisher_user_id`  BIGINT       NOT NULL,
    `created_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_learning_material` PRIMARY KEY (`id`),
    CONSTRAINT `fk_learning_material_group_id` FOREIGN KEY (`group_id`) REFERENCES `recruitment_group` (`id`),
    CONSTRAINT `fk_learning_material_attachment_file_id` FOREIGN KEY (`attachment_file_id`) REFERENCES `stored_file` (`id`),
    CONSTRAINT `fk_learning_material_publisher_user_id` FOREIGN KEY (`publisher_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_stored_file_uploader_user_id` ON `stored_file` (`uploader_user_id`);
CREATE INDEX `idx_stored_file_binding` ON `stored_file` (`binding_type`, `binding_id`);
CREATE INDEX `idx_file_upload_session_uploader_user_id` ON `file_upload_session` (`uploader_user_id`);
CREATE INDEX `idx_recruitment_task_group_id` ON `recruitment_task` (`group_id`);
CREATE INDEX `idx_recruitment_task_deadline_at` ON `recruitment_task` (`deadline_at`);
CREATE INDEX `idx_task_submission_task_id_status` ON `task_submission` (`task_id`, `status`);
CREATE INDEX `idx_task_submission_user_id` ON `task_submission` (`user_id`);
CREATE INDEX `idx_announcement_scope_group_id_created_at` ON `announcement` (`scope`, `group_id`, `created_at`);
CREATE INDEX `idx_learning_material_group_id_created_at` ON `learning_material` (`group_id`, `created_at`);
