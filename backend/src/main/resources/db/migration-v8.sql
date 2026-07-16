-- 页面更新关注 + 统一公开版本快照（可重复执行）。

SET @ddl := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `user_favorite` ADD COLUMN `notify_updates` TINYINT NOT NULL DEFAULT 0 AFTER `description`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_favorite' AND COLUMN_NAME='notify_updates');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `user_favorite` ADD INDEX `idx_fav_page_notify` (`page_id`,`notify_updates`)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_favorite' AND INDEX_NAME='idx_fav_page_notify');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS `wiki_page_version` (
  `id`                 BIGINT       NOT NULL,
  `page_id`            BIGINT       NOT NULL,
  `version`            INT          NOT NULL,
  `path`               VARCHAR(380) NOT NULL,
  `category_slug`      VARCHAR(120) DEFAULT NULL,
  `title`              VARCHAR(200) NOT NULL,
  `icon`               VARCHAR(40)  DEFAULT NULL,
  `description`        VARCHAR(500) DEFAULT NULL,
  `tags`               VARCHAR(500) DEFAULT NULL,
  `headings`           TEXT         DEFAULT NULL,
  `content`            MEDIUMTEXT   DEFAULT NULL,
  `source_type`        VARCHAR(30)  NOT NULL,
  `source_revision_id` BIGINT       DEFAULT NULL,
  `author_id`          BIGINT       DEFAULT NULL,
  `summary`            VARCHAR(300) DEFAULT NULL,
  `published_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_version` (`page_id`,`version`),
  KEY `idx_page_version_time` (`page_id`,`published_at`),
  KEY `idx_page_version_revision` (`source_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 部署前没有统一快照，只能可靠回填每个当前公开页面的现状；后续每次发布均追加完整快照。
-- 以 page.id 作为新表中的初始快照 id（不同表命名空间，不与未来雪花 id 冲突）。
INSERT IGNORE INTO `wiki_page_version`
  (`id`, `page_id`, `version`, `path`, `category_slug`, `title`, `icon`, `description`,
   `tags`, `headings`, `content`, `source_type`, `source_revision_id`, `author_id`, `summary`, `published_at`)
SELECT p.`id`, p.`id`, COALESCE(p.`version`, 0), p.`path`, p.`category_slug`, p.`title`, p.`icon`, p.`description`,
       p.`tags`, p.`headings`, p.`content`, 'MIGRATION', NULL, p.`author_id`, '部署前当前公开版本',
       COALESCE(p.`updated_at`, CURRENT_TIMESTAMP(3))
FROM `wiki_page` p
WHERE p.`deleted` = 0 AND p.`status` = 'PUBLISHED';
