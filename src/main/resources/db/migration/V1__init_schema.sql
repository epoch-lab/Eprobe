CREATE TABLE `user`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `username`       VARCHAR(64)  NOT NULL,
    `password_hash`  VARCHAR(255) NOT NULL,
    `email`          VARCHAR(128) NOT NULL,
    `email_verified` BIT(1)       NOT NULL DEFAULT b'0',
    `role`           VARCHAR(32)  NOT NULL,
    `status`         VARCHAR(32)  NOT NULL,
    `token_version`  BIGINT       NOT NULL DEFAULT 0,
    `last_login_at`  DATETIME(6) NULL,
    `created_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_user` PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_username` UNIQUE (`username`),
    CONSTRAINT `uk_user_email` UNIQUE (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `application`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT       NOT NULL,
    `real_name`           VARCHAR(64)  NOT NULL,
    `phone_number`        VARCHAR(32)  NOT NULL,
    `college`             VARCHAR(128) NOT NULL,
    `major`               VARCHAR(128) NOT NULL,
    `class_name`          VARCHAR(128) NOT NULL,
    `grade`               VARCHAR(32)  NOT NULL,
    `admission_year`      INT          NOT NULL,
    `direction_level1_id` BIGINT       NOT NULL,
    `direction_level2_id` BIGINT       NOT NULL,
    `introduction`        VARCHAR(1000) NULL,
    `status`              VARCHAR(32)  NOT NULL DEFAULT 'SUBMITTED',
    `status_remark`       VARCHAR(255) NULL,
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_application` PRIMARY KEY (`id`),
    CONSTRAINT `uk_application_user_direction` UNIQUE (`user_id`, `direction_level2_id`),
    CONSTRAINT `fk_application_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recruitment_group`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `name`                VARCHAR(128) NOT NULL,
    `direction_level1_id` BIGINT       NOT NULL,
    `direction_level2_id` BIGINT       NOT NULL,
    `grade`               VARCHAR(32)  NOT NULL,
    `admission_year`      INT          NOT NULL,
    `max_size`            INT          NOT NULL,
    `leader_user_id`      BIGINT NULL,
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_recruitment_group` PRIMARY KEY (`id`),
    CONSTRAINT `uk_recruitment_group_name` UNIQUE (`name`),
    CONSTRAINT `fk_recruitment_group_leader_user_id` FOREIGN KEY (`leader_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_member`
(
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `group_id`       BIGINT NOT NULL,
    `user_id`        BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `joined_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_group_member` PRIMARY KEY (`id`),
    CONSTRAINT `uk_group_member_user_group` UNIQUE (`user_id`, `group_id`),
    CONSTRAINT `uk_group_member_application` UNIQUE (`application_id`),
    CONSTRAINT `fk_group_member_group_id` FOREIGN KEY (`group_id`) REFERENCES `recruitment_group` (`id`),
    CONSTRAINT `fk_group_member_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_group_member_application_id` FOREIGN KEY (`application_id`) REFERENCES `application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recruitment_period`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `period_type` VARCHAR(32) NOT NULL,
    `start_time`  DATETIME(6) NOT NULL,
    `end_time`    DATETIME(6) NOT NULL,
    `enabled`     BIT(1)      NOT NULL DEFAULT b'1',
    `created_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_recruitment_period` PRIMARY KEY (`id`),
    CONSTRAINT `uk_recruitment_period_type` UNIQUE (`period_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
