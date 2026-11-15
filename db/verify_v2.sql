-- =============================================================
-- 迁移后自检：每一条都应该返回空或 0
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ai_doctor < db/verify_v2.sql
-- =============================================================

SET NAMES utf8mb4;

-- 1. 每个账号是不是都有且只有一条默认成员
SELECT '① 缺少默认成员的账号（应为空）' AS check_item;
SELECT u.username
FROM users u
WHERE NOT EXISTS (
  SELECT 1 FROM family_members m WHERE m.owner = u.username AND m.is_default = 1
);

-- 2. 有多个默认成员的账号（应为空）
SELECT '② 默认成员重复的账号（应为空）' AS check_item;
SELECT owner, COUNT(*) AS cnt
FROM family_members
WHERE is_default = 1
GROUP BY owner
HAVING COUNT(*) > 1;

-- 3. 没挂上成员的存量数据（应为 0）
SELECT '③ 孤儿行数（都应为 0）' AS check_item;
SELECT 'cabinet_items' AS t, COUNT(*) AS n FROM cabinet_items WHERE member_id IS NULL
UNION ALL SELECT 'medication_plans', COUNT(*) FROM medication_plans WHERE member_id IS NULL
UNION ALL SELECT 'medication_logs',  COUNT(*) FROM medication_logs  WHERE member_id IS NULL
UNION ALL SELECT 'lab_reports',      COUNT(*) FROM lab_reports      WHERE member_id IS NULL
UNION ALL SELECT 'consultations',    COUNT(*) FROM consultations    WHERE member_id IS NULL;

-- 4. 串号检查：数据的 member_id 必须属于它自己的账号（应为空）
SELECT '④ 成员归属与账号不一致（应为空）' AS check_item;
SELECT 'cabinet_items' AS t, c.id
FROM cabinet_items c JOIN family_members m ON m.id = c.member_id
WHERE m.owner <> c.username
UNION ALL
SELECT 'medication_plans', p.id
FROM medication_plans p JOIN family_members m ON m.id = p.member_id
WHERE m.owner <> p.username
UNION ALL
SELECT 'lab_reports', r.id
FROM lab_reports r JOIN family_members m ON m.id = r.member_id
WHERE m.owner <> r.username;

-- 5. 总览：每个成员名下有多少数据
SELECT '⑤ 成员数据总览' AS check_item;
SELECT m.id, m.owner, m.name, m.relation, m.is_default,
       (SELECT COUNT(*) FROM cabinet_items    WHERE member_id = m.id) AS 药箱,
       (SELECT COUNT(*) FROM medication_plans WHERE member_id = m.id) AS 计划,
       (SELECT COUNT(*) FROM medication_logs  WHERE member_id = m.id) AS 打卡,
       (SELECT COUNT(*) FROM lab_reports      WHERE member_id = m.id) AS 化验单
FROM family_members m
ORDER BY m.owner, m.id;
