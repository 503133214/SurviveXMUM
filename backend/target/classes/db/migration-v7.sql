-- 审核时间提精到毫秒：同一秒内多次审核操作（如改判后又通过）时，
-- reviewed_at 若只到秒会相等，导致"最新一次通过 / 上一版快照"排序歧义。
-- 可重复执行：仅当当前精度为 0 时才 ALTER。
SET @need := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wiki_revision'
    AND COLUMN_NAME = 'reviewed_at' AND DATETIME_PRECISION = 0);
SET @ddl := IF(@need > 0,
  'ALTER TABLE `wiki_revision` MODIFY `reviewed_at` DATETIME(3) DEFAULT NULL',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
