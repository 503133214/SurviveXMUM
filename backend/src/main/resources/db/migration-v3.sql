-- 新功能表：收藏 / 浏览历史 / 反馈 / 通知（可重复执行，CREATE IF NOT EXISTS 幂等）。

CREATE TABLE IF NOT EXISTS `user_favorite` (
  `id`          BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `page_id`     BIGINT        NOT NULL,
  `path`        VARCHAR(380)  DEFAULT NULL,
  `title`       VARCHAR(200)  DEFAULT NULL,
  `description` VARCHAR(500)  DEFAULT NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav_user_page` (`user_id`,`page_id`),
  KEY `idx_fav_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_view_history` (
  `id`          BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `page_id`     BIGINT        NOT NULL,
  `path`        VARCHAR(380)  DEFAULT NULL,
  `title`       VARCHAR(200)  DEFAULT NULL,
  `description` VARCHAR(500)  DEFAULT NULL,
  `visited_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hist_user_page` (`user_id`,`page_id`),
  KEY `idx_hist_user_visited` (`user_id`,`visited_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `feedback` (
  `id`          BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `type`        VARCHAR(20)   DEFAULT NULL,            -- bug / feature / ui / other
  `title`       VARCHAR(100)  NOT NULL,
  `content`     VARCHAR(1000) NOT NULL,
  `rating`      INT           NOT NULL DEFAULT 0,
  `contact`     VARCHAR(190)  DEFAULT NULL,
  `status`      VARCHAR(20)   NOT NULL DEFAULT 'pending', -- pending/processing/resolved/rejected（小写，前端约定）
  `reply`       VARCHAR(1000) DEFAULT NULL,
  `reviewer_id` BIGINT        DEFAULT NULL,
  `replied_at`  DATETIME      DEFAULT NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_user` (`user_id`),
  KEY `idx_feedback_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notification` (
  `id`         BIGINT       NOT NULL,
  `user_id`    BIGINT       NOT NULL,                  -- 收件人
  `type`       VARCHAR(30)  DEFAULT NULL,              -- REVISION_APPROVED / REVISION_REJECTED / FEEDBACK_REPLIED
  `title`      VARCHAR(200) DEFAULT NULL,
  `content`    VARCHAR(500) DEFAULT NULL,
  `link`       VARCHAR(400) DEFAULT NULL,              -- 点击跳转的前端路由
  `ref_id`     BIGINT       DEFAULT NULL,              -- 关联的投稿/反馈 id
  `is_read`    TINYINT      NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user_read` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
