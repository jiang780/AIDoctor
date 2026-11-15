-- ============================================================
-- P3 用药安全增强：儿童按体重算剂量 + 添加药品内嵌三层校验
-- 适用库：ai_doctor   执行前建议先备份
-- ============================================================

-- 1) 药箱表增加「单次剂量」字段（可选，记录该药常用剂量，供剂量提示参考）
-- 用 information_schema 判断，避免重复执行时报 Duplicate column
SET @has = (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'cabinet_items' AND column_name = 'dosage');
SET @sql = IF(@has = 0,
  'ALTER TABLE cabinet_items ADD COLUMN dosage VARCHAR(60) NULL COMMENT ''单次剂量，如 10mL / 1袋'' AFTER note',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 药品剂量参考表（按体重 mg/kg，覆盖常见儿科/通用药品）
DROP TABLE IF EXISTS medicine_dosing;
CREATE TABLE medicine_dosing (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  medicine_name       VARCHAR(120) NOT NULL COMMENT '通用名/关键词',
  aliases             VARCHAR(255) NOT NULL DEFAULT '' COMMENT '商品名/别名，逗号分隔',
  mg_per_kg_dose_min  DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '单次最低 mg/kg',
  mg_per_kg_dose_max  DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '单次最高 mg/kg',
  mg_per_kg_day_max   DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '每日最高 mg/kg',
  max_freq_per_day    INT          NOT NULL DEFAULT 0 COMMENT '每日最多服用次数',
  form                VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '剂型',
  strength            VARCHAR(60)  NOT NULL DEFAULT '' COMMENT '浓度，如 100mg/5mL 或 0.125g/袋',
  vol_unit            VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '建议用量单位 mL/袋/片',
  note                VARCHAR(255) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按体重的药品剂量参考（仅供辅助提示，具体请遵医嘱）';

INSERT INTO medicine_dosing
  (medicine_name, aliases, mg_per_kg_dose_min, mg_per_kg_dose_max, mg_per_kg_day_max, max_freq_per_day, form, strength, vol_unit, note)
VALUES
  ('布洛芬', '美林,芬必得,恬倩,托恩,布洛芬混悬液,布洛芬缓释胶囊,布洛芬颗粒',
   5, 10, 40, 4, '混悬液/片', '100mg/5mL', 'mL', '退热镇痛；两次用药至少间隔6小时'),
  ('对乙酰氨基酚', '泰诺林,百服宁,必理通,扑热息痛,小儿退热栓,对乙酰氨基酚滴剂',
   10, 15, 60, 5, '混悬液/栓', '160mg/5mL', 'mL', '两次间隔不少于4小时，24小时内不超过5次'),
  ('阿莫西林', '阿莫仙,阿莫灵,羟氨苄青霉素,阿莫西林颗粒',
   7, 15, 45, 3, '颗粒/干混悬', '0.125g/袋', '袋', '按每日3次折算单次量，疗程遵医嘱'),
  ('头孢克肟', '世福素,立健克,头孢克肟颗粒,头孢克肟干混悬剂',
   3, 6, 12, 2, '颗粒', '50mg/袋', '袋', '每日2次'),
  ('阿奇霉素', '希舒美,阿奇霉素干混悬剂,阿奇霉素颗粒',
   10, 10, 10, 1, '干混悬', '0.1g/袋', '袋', '每日1次，连服3-5天'),
  ('头孢呋辛酯', '西力欣,达力新,头孢呋辛,头孢呋辛酯片',
   10, 15, 30, 2, '片/颗粒', '0.125g/片', '片', '每日2次，饭后服用'),
  ('头孢克洛', '希刻劳,头孢克洛干混悬剂,头孢克洛颗粒',
   7, 13, 40, 3, '颗粒', '0.125g/袋', '袋', '每日3次');
