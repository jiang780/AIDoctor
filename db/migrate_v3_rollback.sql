-- P3 回滚脚本：撤销 migrate_v3.sql 的改动
-- 注意：执行后药箱已记录的 dosage 与 medicine_dosing 表将丢失

ALTER TABLE cabinet_items DROP COLUMN IF EXISTS dosage;
DROP TABLE IF EXISTS medicine_dosing;
