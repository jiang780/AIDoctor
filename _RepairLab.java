import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

/** 一次性修复：按新解析逻辑重算已存化验单的箭头与异常计数 */
public class _RepairLab {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("Main");
        Method parse = c.getDeclaredMethod("parseLooseJson", String.class);
        Method toJson = c.getDeclaredMethod("toJson", Object.class);
        Method flagOf = c.getDeclaredMethod("flagOf", String.class, String.class);
        Method tidy = c.getDeclaredMethod("tidyLabText", String.class);
        Method getConn = c.getDeclaredMethod("getConnection");
        for (Method m : new Method[]{parse, toJson, flagOf, tidy, getConn}) m.setAccessible(true);

        try (Connection conn = (Connection) getConn.invoke(null)) {
            List<int[]> meta = new ArrayList<>();
            List<String> jsons = new ArrayList<>();
            List<String> logs = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, items_json FROM lab_reports ORDER BY id")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String json = rs.getString(2);
                    if (json == null || json.isBlank()) continue;
                    Map<String, Object> parsed = (Map<String, Object>) parse.invoke(null, json);
                    Object raw = parsed == null ? null : parsed.get("items");
                    if (!(raw instanceof List<?> list)) continue;
                    int abn = 0;
                    StringBuilder diff = new StringBuilder();
                    for (Object o : list) {
                        if (!(o instanceof Map)) continue;
                        Map<String, Object> it = (Map<String, Object>) o;
                        String range = (String) tidy.invoke(null, String.valueOf(it.get("range") == null ? "" : it.get("range")));
                        String value = String.valueOf(it.get("value") == null ? "" : it.get("value"));
                        String before = String.valueOf(it.get("flag") == null ? "" : it.get("flag"));
                        String f = (String) flagOf.invoke(null, value, range);
                        String from = "computed";
                        if (f.isEmpty() && !Boolean.TRUE.equals(it.get("verified"))) {
                            Object orig = it.get("flag");
                            if ("↑".equals(orig) || "↓".equals(orig)) { f = (String) orig; from = "original"; }
                        }
                        if (f.isEmpty()) from = "none";
                        if (!before.equals(f)) diff.append("    ").append(it.get("name")).append("  ")
                                .append(value).append(" ref=").append(range)
                                .append("  ").append(before.isEmpty() ? "正常" : before).append(" -> ")
                                .append(f.isEmpty() ? "正常" : f).append("\n");
                        it.put("range", range);
                        it.put("flag", f);
                        it.put("flagFrom", from);
                        it.put("abnormal", !f.isEmpty());
                        if (!f.isEmpty()) abn++;
                    }
                    jsons.add((String) toJson.invoke(null, parsed));
                    meta.add(new int[]{id, abn});
                    if (diff.length() > 0) logs.add("#" + id + " 异常项重算:\n" + diff);
                }
            }
            for (int i = 0; i < meta.size(); i++) {
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE lab_reports SET items_json = ?, abnormal_count = ? WHERE id = ?")) {
                    up.setString(1, jsons.get(i));
                    up.setInt(2, meta.get(i)[1]);
                    up.setInt(3, meta.get(i)[0]);
                    up.executeUpdate();
                }
            }
            for (String s : logs) System.out.print(s);
            System.out.println("repaired reports: " + meta.size());
        }
    }
}
