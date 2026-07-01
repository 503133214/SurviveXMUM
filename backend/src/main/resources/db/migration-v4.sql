-- 赞助 / 致谢墙（超级管理员手动维护，可重复执行）。
CREATE TABLE IF NOT EXISTS `wall_entry` (
  `id`          BIGINT       NOT NULL,
  `name`        VARCHAR(120) NOT NULL,
  `avatar`      VARCHAR(500) DEFAULT NULL,             -- 头像 URL（复用 /wiki/image 上传所得）
  `description` VARCHAR(500) DEFAULT NULL,
  `link`        VARCHAR(500) DEFAULT NULL,
  `category`    VARCHAR(60)  DEFAULT NULL,             -- 分组标签：赞助商 / 特别鸣谢 / 核心贡献者…
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wall_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
