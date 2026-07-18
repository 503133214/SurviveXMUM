-- 将文章内容更新时间与 wiki_page 行级更新解耦（可重复执行）。
-- wiki_page 同时保存浏览量；使用 ON UPDATE 会导致每次阅读都把文章显示为“刚刚更新”。

ALTER TABLE `wiki_page`
  MODIFY COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 修复已经被浏览量更新污染的日期。公开版本快照只在真实发布时写入，
-- 因此当页面时间晚于其最近快照时，以最近快照的发布时间为准。
UPDATE `wiki_page` p
JOIN (
  SELECT `page_id`, MAX(`published_at`) AS `published_at`
  FROM `wiki_page_version`
  GROUP BY `page_id`
) latest ON latest.`page_id` = p.`id`
SET p.`updated_at` = latest.`published_at`
WHERE p.`updated_at` > latest.`published_at`;
