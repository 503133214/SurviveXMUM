-- 管理操作审计日志（可重复执行）。
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`          BIGINT       NOT NULL,
  `actor_id`    BIGINT       DEFAULT NULL,
  `actor_email` VARCHAR(190) DEFAULT NULL,
  `action`      VARCHAR(40)  NOT NULL,             -- REVISION_APPROVE / PAGE_DELETE / USER_UPDATE / BROADCAST …
  `target_type` VARCHAR(20)  DEFAULT NULL,         -- REVISION / PAGE / USER / FEEDBACK / WALL / CATEGORY / SYSTEM
  `target_id`   BIGINT       DEFAULT NULL,
  `detail`      VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_created` (`created_at`),
  KEY `idx_audit_actor` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
