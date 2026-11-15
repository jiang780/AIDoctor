-- =============================================================
-- 回退 migrate_v2.sql（把库恢复到单成员结构）
-- 注意：会丢掉所有成员档案和「这条数据属于谁」的信息，
--       只在迁移失败、还没开始用多成员时执行。
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ai_doctor < db/migrate_v2_rollback.sql
-- =============================================================

SET NAMES utf8mb4;

ALTER TABLE cabinet_items    DROP INDEX idx_member, DROP COLUMN member_id;
ALTER TABLE medication_plans DROP INDEX idx_member, DROP COLUMN member_id;
ALTER TABLE medication_logs  DROP INDEX idx_member, DROP COLUMN member_id;
ALTER TABLE lab_reports      DROP INDEX idx_member, DROP COLUMN member_id;
ALTER TABLE consultations    DROP INDEX idx_member, DROP COLUMN member_id;

DROP TABLE IF EXISTS family_members;
