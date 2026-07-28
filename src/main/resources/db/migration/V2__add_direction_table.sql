CREATE TABLE `direction`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `parent_id`  BIGINT NULL,
    `name`       VARCHAR(64) NOT NULL,
    `level`      INT         NOT NULL,
    `sort_order` INT         NOT NULL DEFAULT 0,
    `enabled`    BIT(1)      NOT NULL DEFAULT b'1',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `pk_direction` PRIMARY KEY (`id`),
    CONSTRAINT `fk_direction_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `direction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_direction_parent_id` ON `direction` (`parent_id`);
CREATE INDEX `idx_direction_level_sort_order` ON `direction` (`level`, `sort_order`, `id`);
