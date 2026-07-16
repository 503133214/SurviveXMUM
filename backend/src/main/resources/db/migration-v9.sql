-- 公开版本作者显示名快照（可重复执行）。
-- 只保存公开昵称或打码邮箱；用户行已删除时可从投稿邮箱快照恢复打码署名，绝不落原始邮箱。

SET @ddl := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `wiki_page_version` ADD COLUMN `author_name` VARCHAR(255) DEFAULT NULL AFTER `author_id`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wiki_page_version' AND COLUMN_NAME='author_name');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `wiki_page_version` v
LEFT JOIN `user` u ON u.`id` = v.`author_id`
LEFT JOIN `wiki_revision` r ON r.`id` = v.`source_revision_id`
SET v.`author_name` = CASE
  WHEN v.`source_type` = 'MIGRATION' AND v.`author_id` IS NULL THEN '系统迁移'
  WHEN u.`id` IS NOT NULL
       AND u.`nickname` IS NOT NULL AND TRIM(u.`nickname`) <> ''
    THEN TRIM(u.`nickname`)
  WHEN u.`id` IS NOT NULL
       AND u.`email` IS NOT NULL AND LOCATE('@', u.`email`) > 1
    THEN CONCAT(
      IF(CHAR_LENGTH(SUBSTRING_INDEX(u.`email`, '@', 1)) <= 2,
         LEFT(SUBSTRING_INDEX(u.`email`, '@', 1), 1),
         LEFT(SUBSTRING_INDEX(u.`email`, '@', 1), 2)),
      '***', SUBSTRING(u.`email`, LOCATE('@', u.`email`)))
  WHEN u.`id` IS NULL
       AND v.`source_type` IN ('REVISION_CREATE', 'REVISION_UPDATE')
       AND r.`id` IS NOT NULL
       AND (v.`author_id` IS NULL OR r.`author_id` = v.`author_id`)
       AND r.`author_email` IS NOT NULL AND LOCATE('@', r.`author_email`) > 1
    THEN CONCAT(
      IF(CHAR_LENGTH(SUBSTRING_INDEX(r.`author_email`, '@', 1)) <= 2,
         LEFT(SUBSTRING_INDEX(r.`author_email`, '@', 1), 1),
         LEFT(SUBSTRING_INDEX(r.`author_email`, '@', 1), 2)),
      '***', SUBSTRING(r.`author_email`, LOCATE('@', r.`author_email`)))
  WHEN v.`source_type` = 'ROLLBACK' OR v.`source_type` LIKE 'ADMIN%'
    THEN '已注销管理员'
  ELSE '已注销贡献者'
END
WHERE v.`author_name` IS NULL OR TRIM(v.`author_name`) = '';
