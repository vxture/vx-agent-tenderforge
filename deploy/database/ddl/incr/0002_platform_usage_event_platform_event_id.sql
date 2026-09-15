-- 0002 · platform_usage_event.platform_event_id：平台 consume 返回的用量事件 id（2026-09-15）
--
-- 《产品接入通则》C3 上行：consume 响应里的 event_id 「存在你自己的请求记录旁可直接对账。
-- 重放时返回的是原始事件的 id——这正是它对账有用的原因」。
--
-- 此前冲洗成功只记 flushed_at，平台那边的事件 id 随响应丢掉：两侧只能拿幂等键去模糊对，
-- 而平台侧查一条事件要的正是这个 id。
--
-- 可空：历史行没有；替身或未返回 event_id 的平台版本也为空。只加在增量里、不改基线，
-- 98 对它的 GRANT 依赖这里先跑（施加顺序：基线 → 增量 → 97 → 98）。
ALTER TABLE local_usage.platform_usage_event
  ADD COLUMN IF NOT EXISTS platform_event_id VARCHAR(64);

COMMENT ON COLUMN local_usage.platform_usage_event.platform_event_id IS
  '平台 /usage/consume 返回的 event_id；重放时为原始事件的 id，用于两侧逐条对账';
