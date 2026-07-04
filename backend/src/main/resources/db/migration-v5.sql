-- 写文章草稿（可重复执行）。半成品不做标题/内容校验，字段均可空。
CREATE TABLE IF NOT EXISTS `wiki_draft` (
  `id`            BIGINT       NOT NULL,
  `user_id`       BIGINT       NOT NULL,
  `type`          VARCHAR(20)  NOT NULL DEFAULT 'CREATE',  -- CREATE / UPDATE
  `target_path`   VARCHAR(380) DEFAULT NULL,               -- UPDATE 草稿 = 目标页路径；CREATE 为 NULL
  `category_slug` VARCHAR(120) DEFAULT NULL,
  `title`         VARCHAR(200) DEFAULT NULL,
  `icon`          VARCHAR(40)  DEFAULT NULL,
  `description`   VARCHAR(500) DEFAULT NULL,
  `tags`          VARCHAR(500) DEFAULT NULL,               -- JSON 数组字符串
  `content`       MEDIUMTEXT   DEFAULT NULL,
  `base_version`  INT          DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_user_path` (`user_id`,`target_path`),  -- 每人每页一份编辑草稿；NULL 不判重，新建草稿可多份
  KEY `idx_draft_user_updated` (`user_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
