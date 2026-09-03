-- 文档页讨论区（可重复执行）。
-- parent_id 记录“直接被回复的那条”，root_id 记录所在楼层；两者都为 NULL 表示这是一条主楼评论。
-- 展示为两层：主楼 + 其下所有回复，回复里再用 @昵称 指向 parent。

CREATE TABLE IF NOT EXISTS `page_comment` (
  `id`         BIGINT        NOT NULL,
  `page_id`    BIGINT        NOT NULL,
  `path`       VARCHAR(380)  NOT NULL,
  `user_id`    BIGINT        DEFAULT NULL,
  `parent_id`  BIGINT        DEFAULT NULL,
  `root_id`    BIGINT        DEFAULT NULL,
  `content`    VARCHAR(1000) NOT NULL,
  `status`     VARCHAR(20)   NOT NULL DEFAULT 'VISIBLE',
  `hidden_reason` VARCHAR(200) DEFAULT NULL,
  `created_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_comment_page` (`page_id`, `status`, `created_at`),
  KEY `idx_comment_user` (`user_id`, `created_at`),
  KEY `idx_comment_root` (`root_id`),
  KEY `idx_comment_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
