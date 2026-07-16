-- SurviveXMUM Wiki schema
-- 在 MySQL 中: CREATE DATABASE IF NOT EXISTS wiki DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 然后 USE wiki; 再执行本文件。

CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT       NOT NULL,
  `email`       VARCHAR(190) NOT NULL,
  `password`    VARCHAR(100) NOT NULL,
  `nickname`    VARCHAR(60)  DEFAULT NULL,
  `avatar`      VARCHAR(255) DEFAULT NULL,
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER / ADMIN
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / BANNED
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wiki_category` (
  `id`          BIGINT       NOT NULL,
  `slug`        VARCHAR(120) NOT NULL,                  -- 如 生活篇
  `label`       VARCHAR(120) NOT NULL,
  `icon`        VARCHAR(40)  DEFAULT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wiki_page` (
  `id`           BIGINT       NOT NULL,
  `category_id`  BIGINT       DEFAULT NULL,
  `category_slug` VARCHAR(120) DEFAULT NULL,
  `slug`         VARCHAR(190) NOT NULL,                 -- 路径末段, 如 医疗
  `path`         VARCHAR(380) NOT NULL,                 -- 完整路由, 如 生活篇/医疗
  `title`        VARCHAR(200) NOT NULL,
  `icon`         VARCHAR(40)  DEFAULT NULL,
  `description`  VARCHAR(500) DEFAULT NULL,
  `tags`         VARCHAR(500) DEFAULT NULL,             -- JSON 数组字符串
  `headings`     TEXT         DEFAULT NULL,             -- JSON 数组字符串(供搜索)
  `content`      MEDIUMTEXT   DEFAULT NULL,             -- markdown
  `sort_order`   INT          NOT NULL DEFAULT 0,
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
  `version`      INT          NOT NULL DEFAULT 0,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  `author_id`    BIGINT       DEFAULT NULL,
  `view_count`   INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wiki_revision` (
  `id`            BIGINT       NOT NULL,
  `page_id`       BIGINT       DEFAULT NULL,            -- 新建页投稿时为空
  `target_path`   VARCHAR(380) NOT NULL,
  `category_slug` VARCHAR(120) DEFAULT NULL,
  `title`         VARCHAR(200) NOT NULL,
  `icon`          VARCHAR(40)  DEFAULT NULL,
  `description`   VARCHAR(500) DEFAULT NULL,
  `tags`          VARCHAR(500) DEFAULT NULL,
  `content`       MEDIUMTEXT   DEFAULT NULL,
  `base_version`  INT          DEFAULT NULL,
  `type`          VARCHAR(20)  NOT NULL,                -- CREATE / UPDATE
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED
  `author_id`     BIGINT       NOT NULL,
  `author_email`  VARCHAR(190) DEFAULT NULL,
  `review_comment` VARCHAR(500) DEFAULT NULL,
  `reviewer_id`   BIGINT       DEFAULT NULL,
  `reviewed_at`   DATETIME     DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rev_status` (`status`),
  KEY `idx_rev_author` (`author_id`),
  KEY `idx_rev_path` (`target_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 页面公开版本快照：与投稿记录解耦，统一记录投稿发布、管理员更新与回滚后的完整页面状态。
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
  `source_type`        VARCHAR(30)  NOT NULL, -- REVISION_CREATE / REVISION_UPDATE / ADMIN_* / ROLLBACK / MIGRATION
  `source_revision_id` BIGINT       DEFAULT NULL,
  `author_id`          BIGINT       DEFAULT NULL,
  `author_name`        VARCHAR(255) DEFAULT NULL, -- 发布时公开显示名快照；不存原始邮箱
  `summary`            VARCHAR(300) DEFAULT NULL,
  `published_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_version` (`page_id`,`version`),
  KEY `idx_page_version_time` (`page_id`,`published_at`),
  KEY `idx_page_version_revision` (`source_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
