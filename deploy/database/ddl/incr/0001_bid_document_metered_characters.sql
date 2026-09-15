-- 0001 · bid_document.metered_characters：字数统计的高水位（2026-09-15）
--
-- tenderforge.document.characters 在导出时只报「本次全文字数高出这个值的部分」，
-- 报完把它抬到本次字数。于是一份标书累计报出的字数 = 它导出过的最大全文字数：
-- 反复导出不虚增，改短不回退，改长只补差额。
--
-- 这是统计维度的去重状态，不是配额。配额与计费走 token 换算的 credits（经 Atlas 计量），
-- 与这一列无关。
--
-- 只加在增量里、不改基线：新库与活库走同一条路径（基线 → 增量 → 97 → 98），
-- PostgresBackedTest 施加的就是活库上真实发生的那一遍。98 给这一列的 GRANT UPDATE
-- 依赖这里先跑——所以增量排在 97/98 之前。
ALTER TABLE bid.bid_document
  ADD COLUMN IF NOT EXISTS metered_characters BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN bid.bid_document.metered_characters IS
  '已上报的全文字数高水位（章节标题+正文的可见字符）；导出时只报超出部分。统计维度，非配额';
