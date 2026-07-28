ALTER TABLE `audit_log`
DROP
FOREIGN KEY `fk_audit_log_actor_user_id`;

ALTER TABLE `audit_log`
    ADD CONSTRAINT `fk_audit_log_actor_user_id`
        FOREIGN KEY (`actor_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

ALTER TABLE `notification`
DROP
FOREIGN KEY `fk_notification_sender_user_id`;

ALTER TABLE `notification`
    ADD CONSTRAINT `fk_notification_sender_user_id`
        FOREIGN KEY (`sender_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

ALTER TABLE `notification`
DROP
FOREIGN KEY `fk_notification_recipient_user_id`;

ALTER TABLE `notification`
    ADD CONSTRAINT `fk_notification_recipient_user_id`
        FOREIGN KEY (`recipient_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;
