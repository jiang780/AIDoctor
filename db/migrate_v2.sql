-- =============================================================
-- AIDoctor v2 迁移：单人档案 → 家庭成员
--
-- 用前必读：
--   1. 先整库备份：mysqldump -uroot -p --default-character-set=utf8mb4 ai_doctor > backup_before_v2.sql
--   2. 执行：mysql -uroot -p --default-character-set=utf8mb4 ai_doctor < db/migrate_v2.sql
--   3. 本脚本只能跑一次（重复执行会在 ADD COLUMN 处报「列已存在」，数据不会被破坏）
--   4. 回退用 db/migrate_v2_rollback.sql
--
-- 设计说明：
--   · health_profiles 不删，降级为「账号资料」（电话/邮箱/备注）；
--     姓名、性别、生日、身高体重、过敏史、基础病搬到 family_members，避免两处存同一份数据。
--   · 年龄不存字段，改存 birth_date。原来的 age 是 varchar，儿童剂量靠它会一直算错。
--   · 收藏 / 浏览记录 / 反馈保持账号级，不加 member_id（本来就不该跟人走）。
-- =============================================================

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 家庭成员表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS family_members (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  owner        VARCHAR(100) NOT NULL COMMENT '所属账号',
  name         VARCHAR(50)  NOT NULL COMMENT '成员姓名',
  relation     VARCHAR(20)  NOT NULL DEFAULT '本人' COMMENT '本人/配偶/父亲/母亲/子女/其他',
  gender       VARCHAR(10)  DEFAULT NULL COMMENT '男/女',
  birth_date   DATE         DEFAULT NULL COMMENT '生日，年龄由程序算，避免逐年失真',
  height_cm    DECIMAL(5,1) DEFAULT NULL,
  weight_kg    DECIMAL(5,1) DEFAULT NULL COMMENT '儿童剂量按体重折算的输入',
  blood_type   VARCHAR(10)  DEFAULT NULL,
  allergies    VARCHAR(500) DEFAULT NULL COMMENT '过敏史，顿号分隔',
  chronic      VARCHAR(500) DEFAULT NULL COMMENT '基础病，顿号分隔（高血压、糖尿病、慢性肾病…）',
  pregnant     VARCHAR(20)  NOT NULL DEFAULT '无' COMMENT '无/备孕/孕早期/孕中期/孕晚期/哺乳期',
  egfr         DECIMAL(5,1) DEFAULT NULL COMMENT '最近一次 eGFR，可从化验单同步',
  alt_value    DECIMAL(6,1) DEFAULT NULL COMMENT '最近一次 ALT',
  renal_stage  VARCHAR(20)  DEFAULT NULL COMMENT 'G1~G5，由 eGFR 推算',
  avatar_seed  VARCHAR(10)  DEFAULT NULL COMMENT '头像底色标识，前端轮换用',
  is_default   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 账号默认成员，旧接口缺省时回落到它',
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_owner (owner),
  KEY idx_owner_default (owner, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭成员健康档案';

-- -------------------------------------------------------------
-- 2. 给按人存的数据加 member_id
-- -------------------------------------------------------------
ALTER TABLE cabinet_items    ADD COLUMN member_id INT NULL COMMENT '归属成员' AFTER username;
ALTER TABLE medication_plans ADD COLUMN member_id INT NULL COMMENT '归属成员' AFTER username;
ALTER TABLE medication_logs  ADD COLUMN member_id INT NULL COMMENT '归属成员' AFTER username;
ALTER TABLE lab_reports      ADD COLUMN member_id INT NULL COMMENT '归属成员' AFTER username;
ALTER TABLE consultations    ADD COLUMN member_id INT NULL COMMENT '归属成员（P1 只加字段，UI 暂不切）' AFTER patient_username;

-- -------------------------------------------------------------
-- 3. 回填 3.1：有健康档案的账号，各建一条「本人」
-- -------------------------------------------------------------
INSERT INTO family_members
  (owner, name, relation, gender, birth_date, height_cm, weight_kg, blood_type, allergies, chronic, is_default, avatar_seed)
SELECT
  hp.username,
  COALESCE(NULLIF(TRIM(hp.name), ''), hp.username),
  '本人',
  NULLIF(TRIM(hp.gender), ''),
  CASE WHEN hp.age REGEXP '^[0-9]{1,3}$'
       THEN DATE_SUB(CURDATE(), INTERVAL CAST(hp.age AS UNSIGNED) YEAR) END,
  CASE WHEN hp.height REGEXP '^[0-9]+(\\.[0-9]+)?$'
       THEN CAST(hp.height AS DECIMAL(5,1)) END,
  CASE WHEN hp.weight REGEXP '^[0-9]+(\\.[0-9]+)?$'
       THEN CAST(hp.weight AS DECIMAL(5,1)) END,
  NULLIF(TRIM(hp.blood_type), ''),
  NULLIF(TRIM(hp.allergies), ''),
  NULLIF(TRIM(hp.chronic), ''),
  1,
  'm0'
FROM health_profiles hp
WHERE hp.username IS NOT NULL AND hp.username <> ''
  AND NOT EXISTS (SELECT 1 FROM family_members fm WHERE fm.owner = hp.username);

-- -------------------------------------------------------------
-- 4. 回填 3.2：只有登录账号、还没填过档案的，也建一条
-- -------------------------------------------------------------
INSERT INTO family_members (owner, name, relation, is_default, avatar_seed)
SELECT u.username, u.username, '本人', 1, 'm0'
FROM users u
WHERE u.username IS NOT NULL AND u.username <> ''
  AND NOT EXISTS (SELECT 1 FROM family_members fm WHERE fm.owner = u.username);

-- -------------------------------------------------------------
-- 5. 把存量数据挂到各账号的「本人」上
-- -------------------------------------------------------------
UPDATE cabinet_items c
  JOIN family_members m ON m.owner = c.username AND m.is_default = 1
SET c.member_id = m.id
WHERE c.member_id IS NULL;

UPDATE medication_plans p
  JOIN family_members m ON m.owner = p.username AND m.is_default = 1
SET p.member_id = m.id
WHERE p.member_id IS NULL;

UPDATE medication_logs l
  JOIN family_members m ON m.owner = l.username AND m.is_default = 1
SET l.member_id = m.id
WHERE l.member_id IS NULL;

UPDATE lab_reports r
  JOIN family_members m ON m.owner = r.username AND m.is_default = 1
SET r.member_id = m.id
WHERE r.member_id IS NULL;

UPDATE consultations c
  JOIN family_members m ON m.owner = c.patient_username AND m.is_default = 1
SET c.member_id = m.id
WHERE c.member_id IS NULL;

-- -------------------------------------------------------------
-- 6. 查询索引（按成员取数据是新的主路径）
-- -------------------------------------------------------------
ALTER TABLE cabinet_items    ADD KEY idx_member (member_id);
ALTER TABLE medication_plans ADD KEY idx_member (member_id);
ALTER TABLE medication_logs  ADD KEY idx_member (member_id);
ALTER TABLE lab_reports      ADD KEY idx_member (member_id);
ALTER TABLE consultations    ADD KEY idx_member (member_id);

-- -------------------------------------------------------------
-- 7. 唯一键要带上成员，否则同一账号下两个人有同一种药会冲突
-- -------------------------------------------------------------
ALTER TABLE cabinet_items
  DROP INDEX uk_cabinet,
  ADD UNIQUE KEY uk_cabinet (username, member_id, medicine_name, spec);
