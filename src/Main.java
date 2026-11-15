import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

public class Main {
    private static final int PORT = 8080;
    private static final String DB_URL = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/ai_doctor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    /** 本地配置（config.properties），用于放置 OCR 等第三方密钥，避免每次都改环境变量 */
    private static final Map<String, String> CONFIG = loadConfig();
    /** 百度 access_token 缓存（有效期 30 天，这里保守缓存 25 分钟） */
    private static String ocrToken = "";
    private static long ocrTokenTime = 0L;

    private static final List<Map<String, Object>> MEDICINE_DATA = List.of(
            medicine("对乙酰氨基酚缓释片", "退烧止痛", "适用于发热、头痛、关节痛等轻中度疼痛。", "¥15.8"),
            medicine("连花清瘟胶囊", "感冒类", "用于风热感冒引起的发热、咽痛、鼻塞等症状。", "¥29.9"),
            medicine("阿莫西林分散片", "抗感染", "用于敏感菌引起的呼吸道、泌尿道感染。", "¥18.5"),
            medicine("孟鲁司特钠片", "过敏类", "用于缓解季节性过敏性鼻炎和哮喘相关症状。", "¥26.0"),
            medicine("奥司他韦胶囊", "抗病毒", "用于流感病毒感染，可在早期使用控制病情。", "¥42.0"),
            medicine("复方阿司匹林", "消炎止痛", "适用于轻度到中度疼痛和炎症反应。", "¥22.4"),
            medicine("维生素C泡腾片", "营养补充", "适用于日常营养补充和增强抵抗力。", "¥17.2"),
            medicine("双歧杆菌三联活菌散", "肠胃调理", "用于调节肠道菌群，改善消化不良。", "¥33.5"),
            medicine("龙胆泻肝丸", "中成药", "用于肝火上炎引起的口苦、目赤、胸闷等。", "¥38.9"),
            medicine("布洛芬缓释胶囊", "止痛类", "用于缓解发热、头痛、牙痛和关节痛。", "¥21.7"),
            medicine("感冒清热颗粒", "感冒类", "用于风热感冒引起的发热、咽痛等症状。", "¥18.9"),
            medicine("头孢克洛缓释片", "抗感染", "用于敏感菌引起的呼吸道和中耳感染。", "¥24.8"),
            medicine("盐酸氨溴索片", "止咳类", "用于咳嗽、痰多等症状的辅助治疗。", "¥19.6"),
            medicine("糖尿病血糖平片", "慢病类", "用于血糖控制辅助治疗，需遵医嘱使用。", "¥47.6")
    );

    private static final List<Map<String, Object>> DISEASE_DATA = List.of(
            disease("上呼吸道感染", "常见感染", "多表现为鼻塞、流涕、咽痛、低热，通常与受凉或病毒感染有关。"),
            disease("急性扁桃体炎", "咽喉疾病", "常见咽痛明显、吞咽困难、口臭，部分伴有发热和淋巴结肿大。"),
            disease("过敏性鼻炎", "过敏类", "反复打喷嚏、鼻痒、流清鼻涕，通常在季节变化或接触过敏原时加重。"),
            disease("胃炎", "消化类", "可见上腹隐痛、反酸、嗳气，部分患者伴恶心、食欲下降。"),
            disease("急性肠胃炎", "消化类", "典型表现为腹痛、腹泻、恶心、呕吐，需注意补液和饮食管理。"),
            disease("支气管炎", "呼吸类", "常见咳嗽、痰多、胸闷，可能由感染、刺激物或过敏诱发。"),
            disease("流行性感冒", "常见感染", "由流感病毒引起，起病急，常伴高热、全身酸痛、乏力，传染性较强。"),
            disease("哮喘", "呼吸类", "反复发作的喘息、气促、胸闷或咳嗽，常在夜间或接触过敏原时加重。"),
            disease("胃食管反流病", "消化类", "胃内容物反流引起烧心、反酸、胸骨后不适，常在餐后或平卧时加重。"),
            disease("偏头痛", "神经类", "反复发作的一侧搏动性头痛，常伴恶心、畏光、畏声。"),
            disease("高血压", "慢病类", "以体循环动脉压升高为主要表现，多数起病隐匿，需长期监测和管理。"),
            disease("2型糖尿病", "慢病类", "以血糖升高为特征，常见多饮、多食、多尿、体重下降等表现。"),
            disease("慢性咽炎", "咽喉疾病", "咽部异物感、干痒、灼热，晨起刺激性干咳，病程较长。"),
            disease("中耳炎", "耳鼻喉类", "耳痛、听力下降、耳闷，部分伴有发热或耳流脓。"),
            disease("结膜炎", "眼科类", "眼红、异物感、流泪、分泌物增多，可伴明显痒感。"),
            disease("湿疹", "皮肤类", "皮肤红斑、丘疹、瘙痒，反复发作，可伴渗出或脱屑。"),
            disease("颈椎病", "骨关节类", "颈肩部酸痛、僵硬，可伴上肢麻木、头晕等。"),
            disease("泌尿系感染", "泌尿类", "尿频、尿急、尿痛，可伴下腹不适或发热。"),
            disease("肺炎", "呼吸类", "发热、咳嗽、咳痰，可伴胸痛、气促，需及时就医。"),
            disease("失眠症", "神经类", "入睡困难、易醒、早醒或睡眠质量差，影响日间状态。")
    );

    private static final List<String> SYMPTOMS = List.of(
            "发热", "咳嗽", "咽喉痛", "鼻塞", "头痛", "乏力",
            "腹痛", "腹泻", "恶心", "打喷嚏", "流鼻涕", "胸闷"
    );

    /** 读取 config.properties（支持 # 注释、key=value），文件不存在时返回空表 */
    private static Map<String, String> loadConfig() {
        Map<String, String> map = new HashMap<>();
        for (String name : new String[]{"config.properties", "config.local.properties"}) {
            Path path = Path.of(name);
            if (!Files.exists(path)) continue;
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String text = line.trim();
                    if (text.isEmpty() || text.startsWith("#") || text.startsWith("//")) continue;
                    int eq = text.indexOf('=');
                    if (eq <= 0) continue;
                    map.put(text.substring(0, eq).trim(), text.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                System.err.println("读取 " + name + " 失败：" + e.getMessage());
            }
        }
        return map;
    }

    /** 配置优先级：环境变量 > config.properties > 默认值 */
    private static String cfg(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env.trim();
        String value = CONFIG.get(key);
        if (value != null && !value.isBlank()) return value.trim();
        return fallback;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        try {
            initializeDatabase();
        } catch (Exception e) {
            System.err.println("数据库初始化失败，程序将使用内存默认数据：" + e.getMessage());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/health", Main::handleHealth);
        server.createContext("/api/register", Main::handleRegister);
        server.createContext("/api/login", Main::handleLogin);
        server.createContext("/api/medicines", Main::handleMedicines);
        server.createContext("/api/diseases/view", Main::handleDiseaseView);
        server.createContext("/api/diseases", Main::handleDiseases);
        server.createContext("/api/identify", Main::handleIdentify);
        server.createContext("/api/ocr", Main::handleOcr);
        server.createContext("/api/ocr/status", Main::handleOcrStatus);
        server.createContext("/api/search", Main::handleSearch);
        server.createContext("/api/feedback", Main::handleFeedback);
        server.createContext("/api/admin/feedback", Main::handleAdminFeedback);
        server.createContext("/api/admin/medicine", Main::handleAdminMedicine);
        server.createContext("/api/admin/disease", Main::handleAdminDisease);
        server.createContext("/api/symptoms", Main::handleSymptoms);
        server.createContext("/api/diagnose", Main::handleDiagnose);
        server.createContext("/api/ai-consult", Main::handleAIConsult);
        server.createContext("/api/cabinet", Main::handleCabinet);
        server.createContext("/api/members", Main::handleMembers);
        server.createContext("/api/safety", Main::handleSafety);
        server.createContext("/api/safety/preview", Main::handleSafetyPreview);
        server.createContext("/api/dose-suggest", Main::handleDoseSuggest);
        server.createContext("/api/lab-report", Main::handleLabReport);
        server.createContext("/api/plans", Main::handlePlans);
        server.createContext("/api/favorites", Main::handleFavorites);
        server.createContext("/api/history", Main::handleHistory);
        server.createContext("/api/profile", Main::handleProfile);
        server.createContext("/api/records", Main::handleRecords);
        server.createContext("/api/doctor/register", Main::handleDoctorRegister);
        server.createContext("/api/doctor/login", Main::handleDoctorLogin);
        server.createContext("/api/doctor/consultations", Main::handleDoctorConsultations);
        server.createContext("/api/doctor/reply", Main::handleDoctorReply);
        server.createContext("/api/doctor/patients", Main::handleDoctorPatients);
        server.createContext("/api/doctor/prescriptions", Main::handleDoctorPrescriptions);
        server.createContext("/api/doctor/safety", Main::handleDoctorSafety);
        server.createContext("/api/doctor/patient-members", Main::handleDoctorPatientMembers);
        server.createContext("/api/consult", Main::handleConsult);
        server.createContext("/api/consultations", Main::handlePatientConsultations);
        server.createContext("/api/notifications", Main::handleNotifications);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("AIDoctor API 已启动，访问地址：http://localhost:" + PORT + "/login.html");
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, Map.of("status", "ok", "service", "ai-doctor", "db", "mysql"));
    }

    private static void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }

        Map<String, Object> request = readJson(exchange);
        String username = String.valueOf(request.getOrDefault("username", "")).trim();
        String password = String.valueOf(request.getOrDefault("password", "")).trim();

        if (username.isBlank() || password.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "账号和密码不能为空"));
            return;
        }

        if (username.length() < 3 || password.length() < 4) {
            sendJson(exchange, 400, Map.of("success", false, "message", "账号至少3位，密码至少4位"));
            return;
        }

        boolean created = registerUser(username, password);
        if (created) {
            sendJson(exchange, Map.of(
                    "success", true,
                    "message", "注册成功",
                    "user", Map.of("username", username, "loggedIn", true)
            ));
            return;
        }

        sendJson(exchange, 409, Map.of("success", false, "message", "账号已存在，请换一个用户名"));
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }

        Map<String, Object> request = readJson(exchange);
        String username = String.valueOf(request.getOrDefault("username", "")).trim();
        String password = String.valueOf(request.getOrDefault("password", "")).trim();

        boolean valid = validateLogin(username, password);
        if (valid) {
            sendJson(exchange, Map.of(
                    "success", true,
                    "message", "登录成功",
                    "user", Map.of("username", username, "loggedIn", true)
            ));
            return;
        }

        sendJson(exchange, 401, Map.of("success", false, "message", "账号或密码错误，请使用演示账号：admin / 123456"));
    }

    private static void handleMedicines(HttpExchange exchange) throws IOException {
        String keyword = readQueryParam(exchange, "keyword");
        List<Map<String, Object>> medicines = loadMedicinesFromDb(keyword);
        sendJson(exchange, medicines);
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS ai_doctor");
            }
        } catch (SQLException e) {
            throw new SQLException("无法连接到 MySQL，先在 Navicat 中创建本地 MySQL 连接并确保端口 3306 可用。", e);
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS medicines (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(200) NOT NULL, " +
                    "category VARCHAR(100) NOT NULL, " +
                    "use_desc VARCHAR(500) NOT NULL, " +
                    "price VARCHAR(50) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            stmt.executeUpdate(
                    "INSERT INTO users (username, password) VALUES ('admin', '123456') " +
                    "ON DUPLICATE KEY UPDATE password = VALUES(password)"
            );
            // 药品名称唯一：改为幂等 upsert，避免每次启动清空表（后台新增的药品得以保留）
            try {
                stmt.executeUpdate("ALTER TABLE medicines ADD UNIQUE KEY uk_medicines_name (name)");
            } catch (SQLException ignored) {
                // 索引已存在
            }
            // 药品种子数据：每次启动幂等写入（与 mysql_init.sql 保持一致）
            // 感冒类 / 退烧止痛 / 抗感染 / 抗病毒
            stmt.executeUpdate(
                    "INSERT INTO medicines (name, category, use_desc, price) VALUES " +
                    "('对乙酰氨基酚缓释片', '退烧止痛', '适用于发热、头痛、关节痛等轻中度疼痛。', '¥15.8'), " +
                    "('布洛芬缓释胶囊', '止痛类', '用于缓解发热、头痛、牙痛和关节痛。', '¥21.7'), " +
                    "('复方阿司匹林', '消炎止痛', '适用于轻度到中度疼痛和炎症反应。', '¥22.4'), " +
                    "('布洛芬混悬液（美林）', '儿童用药', '用于1-12岁儿童退热，按体重给药。', '¥28.0'), " +
                    "('对乙酰氨基酚滴剂', '儿童用药', '用于婴幼儿退热，需按年龄体重精确给药。', '¥25.0'), " +
                    "('阿司匹林肠溶片', '退烧止痛', '解热镇痛；小剂量抗血小板需遵医嘱使用。', '¥12.0'), " +
                    "('复方对乙酰氨基酚片', '退烧止痛', '用于头痛、牙痛、神经痛及感冒发热。', '¥13.5'), " +
                    "('双氯芬酸钠缓释片', '消炎止痛', '用于各类关节炎及软组织损伤引起的疼痛。', '¥19.0'), " +
                    "('洛索洛芬钠片', '消炎止痛', '用于骨关节炎、肩周炎、牙痛等疼痛。', '¥23.5'), " +
                    "('塞来昔布胶囊', '消炎止痛', '用于骨关节炎、类风湿关节炎引起的疼痛。', '¥45.0'), " +
                    "('酚咖片', '退烧止痛', '用于普通感冒或流感引起的发热、头痛。', '¥14.5'), " +
                    "('连花清瘟胶囊', '感冒类', '用于风热感冒引起的发热、咽痛、鼻塞等症状。', '¥29.9'), " +
                    "('感冒清热颗粒', '感冒类', '用于风热感冒引起的发热、咽痛等症状。', '¥18.9'), " +
                    "('复方氨酚烷胺片', '感冒类', '用于感冒及流感引起的发热、头痛、鼻塞、流涕。', '¥15.5'), " +
                    "('感冒灵颗粒', '感冒类', '缓解感冒引起的头痛、发热、鼻塞、咽痛。', '¥13.9'), " +
                    "('氨咖黄敏胶囊', '感冒类', '用于缓解普通感冒及流感引起的发热头痛。', '¥9.9'), " +
                    "('双黄连口服液', '感冒类', '用于风热感冒引起的发热、咳嗽、咽痛。', '¥22.0'), " +
                    "('板蓝根颗粒', '感冒类', '清热解毒，用于咽喉肿痛、口咽干燥。', '¥12.5'), " +
                    "('小柴胡颗粒', '感冒类', '用于外感病邪犯少阳证，寒热往来、食欲不振。', '¥16.8'), " +
                    "('蒲地蓝消炎口服液', '感冒类', '清热解毒，用于咽炎、扁桃体炎、腮腺炎。', '¥32.0'), " +
                    "('维C银翘片', '感冒类', '用于流行性感冒引起的发热、头痛、咽痛。', '¥8.5'), " +
                    "('银翘解毒片', '感冒类', '用于风热感冒发热头痛、咳嗽口干。', '¥11.0'), " +
                    "('荆防颗粒', '感冒类', '用于风寒感冒引起的头痛身痛、恶寒无汗。', '¥14.8'), " +
                    "('金花清感颗粒', '感冒类', '用于流感风热犯肺证，发热、咽痛、咳嗽。', '¥39.0'), " +
                    "('阿莫西林分散片', '抗感染', '用于敏感菌引起的呼吸道、泌尿道感染。', '¥18.5'), " +
                    "('头孢克洛缓释片', '抗感染', '用于敏感菌引起的呼吸道和中耳感染。', '¥24.8'), " +
                    "('头孢克肟颗粒', '抗感染', '用于敏感菌引起的呼吸道、泌尿道感染。', '¥28.0'), " +
                    "('头孢氨苄胶囊', '抗感染', '用于敏感菌引起的皮肤软组织感染。', '¥13.8'), " +
                    "('阿奇霉素分散片', '抗感染', '用于支原体、衣原体感染及呼吸道感染。', '¥21.0'), " +
                    "('罗红霉素胶囊', '抗感染', '用于敏感菌引起的呼吸道、耳鼻喉感染。', '¥16.5'), " +
                    "('克拉霉素片', '抗感染', '幽门螺杆菌根除方案的常用抗生素之一。', '¥26.0'), " +
                    "('左氧氟沙星片', '抗感染', '用于呼吸道、泌尿道、肠道细菌感染。', '¥19.5'), " +
                    "('甲硝唑片', '抗感染', '用于厌氧菌感染、滴虫病及口腔感染。', '¥6.5'), " +
                    "('诺氟沙星胶囊', '抗感染', '用于肠道感染、尿路感染。', '¥8.8'), " +
                    "('复方磺胺甲噁唑片', '抗感染', '用于敏感菌引起的尿路、呼吸道感染。', '¥11.5'), " +
                    "('奥司他韦胶囊', '抗病毒', '用于流感病毒感染，可在早期使用控制病情。', '¥42.0'), " +
                    "('利巴韦林颗粒', '抗病毒', '用于呼吸道合胞病毒引起的病毒性感染。', '¥12.0'), " +
                    "('阿昔洛韦片', '抗病毒', '用于疱疹病毒感染，如带状疱疹、水痘。', '¥15.0'), " +
                    "('抗病毒口服液', '抗病毒', '用于风热感冒、流感及病毒性感冒。', '¥19.9'), " +
                    "('蓝芩口服液', '抗病毒', '清热解毒，用于咽痛、咽干、急性咽炎。', '¥36.0') " +
                    "ON DUPLICATE KEY UPDATE category = VALUES(category), use_desc = VALUES(use_desc), price = VALUES(price)"
            );
            // 止咳化痰 / 过敏 / 消化系统
            stmt.executeUpdate(
                    "INSERT INTO medicines (name, category, use_desc, price) VALUES " +
                    "('盐酸氨溴索片', '止咳类', '用于咳嗽、痰多等症状的辅助治疗。', '¥19.6'), " +
                    "('氨溴特罗口服液', '止咳类', '用于急慢性呼吸道疾病引起的咳嗽、痰多。', '¥28.5'), " +
                    "('复方甘草片', '止咳类', '用于镇咳祛痰。', '¥7.5'), " +
                    "('右美沙芬缓释片', '止咳类', '用于感冒、支气管炎等引起的干咳。', '¥16.0'), " +
                    "('急支糖浆', '止咳类', '用于外感风热所致咳嗽、发热、痰黄。', '¥15.9'), " +
                    "('蜜炼川贝枇杷膏', '止咳类', '用于伤风咳嗽、痰多咽干。', '¥26.5'), " +
                    "('苏黄止咳胶囊', '止咳类', '用于感染后咳嗽、咳嗽变异性哮喘。', '¥45.0'), " +
                    "('复方甲氧那明胶囊', '止咳类', '用于支气管哮喘和咳嗽变异性哮喘。', '¥24.0'), " +
                    "('氨茶碱片', '止咳类', '用于支气管哮喘、慢性喘息型支气管炎。', '¥9.0'), " +
                    "('沙丁胺醇气雾剂', '止咳类', '用于缓解哮喘急性发作的喘息症状。', '¥22.0'), " +
                    "('桉柠蒎肠溶软胶囊', '止咳类', '用于急慢性鼻窦炎、支气管炎的祛痰。', '¥32.0'), " +
                    "('孟鲁司特钠片', '过敏类', '用于缓解季节性过敏性鼻炎和哮喘相关症状。', '¥26.0'), " +
                    "('氯雷他定片', '过敏类', '用于过敏性鼻炎、慢性荨麻疹。', '¥19.9'), " +
                    "('西替利嗪片', '过敏类', '用于季节性和常年性过敏性鼻炎。', '¥15.0'), " +
                    "('马来酸氯苯那敏片', '过敏类', '用于皮肤黏膜过敏、荨麻疹、药疹。', '¥3.5'), " +
                    "('依巴斯汀片', '过敏类', '用于过敏性鼻炎、慢性荨麻疹。', '¥28.0'), " +
                    "('地氯雷他定干混悬剂', '过敏类', '用于儿童过敏性鼻炎、荨麻疹。', '¥32.0'), " +
                    "('糠酸莫米松鼻喷雾剂', '过敏类', '用于成人及儿童过敏性鼻炎。', '¥48.0'), " +
                    "('布地奈德鼻喷雾剂', '过敏类', '用于常年性及季节性过敏性鼻炎。', '¥55.0'), " +
                    "('奥美拉唑肠溶胶囊', '消化类', '用于胃溃疡、十二指肠溃疡、反流性食管炎。', '¥18.0'), " +
                    "('雷贝拉唑钠肠溶片', '消化类', '用于胃食管反流病、消化性溃疡。', '¥32.0'), " +
                    "('铝碳酸镁咀嚼片', '消化类', '用于胃痛、胃灼热、反酸、嗳气。', '¥27.0'), " +
                    "('多潘立酮片', '消化类', '用于消化不良、腹胀、恶心呕吐。', '¥15.5'), " +
                    "('枸橼酸莫沙必利片', '消化类', '用于功能性消化不良伴胃灼热、嗳气。', '¥21.0'), " +
                    "('法莫替丁片', '消化类', '用于胃酸过多、胃及十二指肠溃疡。', '¥12.0'), " +
                    "('蒙脱石散', '消化类', '用于成人及儿童急慢性腹泻。', '¥16.0'), " +
                    "('双歧杆菌三联活菌散', '肠胃调理', '用于调节肠道菌群，改善消化不良。', '¥33.5'), " +
                    "('健胃消食片', '肠胃调理', '用于脾胃虚弱、消化不良、脘腹胀满。', '¥10.0'), " +
                    "('藿香正气水', '肠胃调理', '用于外感风寒、内伤湿滞引起的腹泻呕吐。', '¥9.9'), " +
                    "('保和丸', '肠胃调理', '用于食积停滞、脘腹胀满、嗳腐吞酸。', '¥11.5'), " +
                    "('肠炎宁片', '肠胃调理', '用于急慢性胃肠炎、腹泻。', '¥18.5'), " +
                    "('复方黄连素片', '肠胃调理', '用于肠道感染引起的腹泻腹痛。', '¥13.0') " +
                    "ON DUPLICATE KEY UPDATE category = VALUES(category), use_desc = VALUES(use_desc), price = VALUES(price)"
            );
            // 慢病 / 中成药 / 营养 / 皮肤外用 / 五官科 / 儿童用药
            stmt.executeUpdate(
                    "INSERT INTO medicines (name, category, use_desc, price) VALUES " +
                    "('糖尿病血糖平片', '慢病类', '用于血糖控制辅助治疗，需遵医嘱使用。', '¥47.6'), " +
                    "('苯磺酸氨氯地平片', '慢病类', '用于高血压和心绞痛，需遵医嘱长期服用。', '¥32.0'), " +
                    "('硝苯地平控释片', '慢病类', '用于高血压、稳定型心绞痛。', '¥42.0'), " +
                    "('缬沙坦胶囊', '慢病类', '用于轻中度高血压的降压治疗。', '¥38.0'), " +
                    "('琥珀酸美托洛尔缓释片', '慢病类', '用于高血压、心绞痛、心律失常。', '¥35.0'), " +
                    "('盐酸二甲双胍缓释片', '慢病类', '2型糖尿病的一线降糖药物。', '¥18.0'), " +
                    "('阿卡波糖片', '慢病类', '用于降低2型糖尿病患者餐后血糖。', '¥58.0'), " +
                    "('格列美脲片', '慢病类', '用于2型糖尿病的血糖控制。', '¥26.0'), " +
                    "('阿托伐他汀钙片', '慢病类', '用于高胆固醇血症，预防心血管事件。', '¥35.0'), " +
                    "('非诺贝特胶囊', '慢病类', '用于高甘油三酯血症。', '¥22.0'), " +
                    "('复方丹参滴丸', '慢病类', '用于冠心病心绞痛、胸闷气短。', '¥29.0'), " +
                    "('硝酸甘油片', '慢病类', '用于心绞痛急性发作，需舌下含服。', '¥9.5'), " +
                    "('稳心颗粒', '慢病类', '用于心悸、气短、早搏。', '¥32.0'), " +
                    "('龙胆泻肝丸', '中成药', '用于肝火上炎引起的口苦、目赤、胸闷等。', '¥38.9'), " +
                    "('六味地黄丸', '中成药', '用于肾阴亏损、头晕耳鸣、腰膝酸软。', '¥22.0'), " +
                    "('逍遥丸', '中成药', '用于肝郁脾虚、月经不调、胸胁胀痛。', '¥15.0'), " +
                    "('归脾丸', '中成药', '用于心脾两虚、失眠多梦、食欲不振。', '¥14.5'), " +
                    "('补中益气丸', '中成药', '用于脾胃虚弱、体倦乏力、食少腹胀。', '¥13.0'), " +
                    "('牛黄解毒片', '中成药', '用于火热内盛、咽喉肿痛、口舌生疮。', '¥8.0'), " +
                    "('清开灵颗粒', '中成药', '用于病毒性感冒、急性咽炎引起的高热。', '¥17.0'), " +
                    "('云南白药膏', '中成药', '活血化瘀，用于跌打损伤、风湿疼痛。', '¥28.0'), " +
                    "('正红花油', '中成药', '用于跌打损伤、风湿骨痛、蚊虫叮咬。', '¥7.8'), " +
                    "('风油精', '中成药', '用于伤风感冒引起的头痛头晕、蚊虫叮咬。', '¥5.5'), " +
                    "('维生素C泡腾片', '营养补充', '适用于日常营养补充和增强抵抗力。', '¥17.2'), " +
                    "('复合维生素B片', '营养补充', '用于B族维生素缺乏引起的营养不良。', '¥9.5'), " +
                    "('维生素E软胶囊', '营养补充', '用于心脑血管疾病的辅助治疗及抗氧化。', '¥13.0'), " +
                    "('维生素AD滴剂', '营养补充', '用于维生素A、D缺乏，预防佝偻病。', '¥21.0'), " +
                    "('碳酸钙D3片', '营养补充', '用于成人及老年人补钙、预防骨质疏松。', '¥36.0'), " +
                    "('葡萄糖酸锌口服液', '营养补充', '用于缺锌引起的食欲不振、发育迟缓。', '¥19.0'), " +
                    "('多维元素片', '营养补充', '用于预防和治疗维生素与矿物质缺乏。', '¥38.0'), " +
                    "('琥珀酸亚铁片', '营养补充', '用于缺铁性贫血的预防和治疗。', '¥25.0'), " +
                    "('莫匹罗星软膏', '皮肤外用', '用于脓疱疮、毛囊炎等细菌性皮肤感染。', '¥22.0'), " +
                    "('红霉素软膏', '皮肤外用', '用于脓疱疮、小面积烧烫伤、寻常痤疮。', '¥4.5'), " +
                    "('阿达帕林凝胶', '皮肤外用', '用于寻常痤疮的粉刺、丘疹治疗。', '¥26.0'), " +
                    "('氢化可的松乳膏', '皮肤外用', '用于过敏性皮炎、湿疹、神经性皮炎。', '¥8.5'), " +
                    "('炉甘石洗剂', '皮肤外用', '用于痱子、荨麻疹等急性瘙痒性皮肤病。', '¥7.0'), " +
                    "('特比萘芬乳膏', '皮肤外用', '用于手足癣、体癣、股癣等真菌感染。', '¥16.0'), " +
                    "('酮康唑乳膏', '皮肤外用', '用于真菌感染引起的皮肤癣病。', '¥13.5'), " +
                    "('曲安奈德益康唑乳膏', '皮肤外用', '用于皮炎湿疹伴发真菌感染。', '¥15.0'), " +
                    "('创可贴', '皮肤外用', '用于小创口、擦伤的应急包扎。', '¥3.0'), " +
                    "('碘伏消毒液', '皮肤外用', '用于皮肤、黏膜小伤口的消毒处理。', '¥6.5'), " +
                    "('氧氟沙星滴眼液', '五官科', '用于结膜炎、角膜炎等细菌性眼部感染。', '¥12.0'), " +
                    "('玻璃酸钠滴眼液', '五官科', '用于干眼症，缓解眼睛干涩疲劳。', '¥28.0'), " +
                    "('左氧氟沙星滴眼液', '五官科', '用于睑缘炎、结膜炎等外眼部感染。', '¥15.0'), " +
                    "('复方薄荷脑滴鼻液', '五官科', '用于干燥性鼻炎、鼻出血的湿润保护。', '¥8.0'), " +
                    "('西瓜霜含片', '五官科', '用于咽喉肿痛、口舌生疮。', '¥10.5'), " +
                    "('金嗓子喉片', '五官科', '用于急慢性咽喉炎、声音嘶哑。', '¥9.0'), " +
                    "('咽炎片', '五官科', '用于慢性咽炎引起的咽干、咽痒、干咳。', '¥14.0'), " +
                    "('开喉剑喷雾剂', '五官科', '用于急慢性咽喉炎、口腔溃疡。', '¥28.0'), " +
                    "('小儿氨酚黄那敏颗粒', '儿童用药', '用于儿童普通感冒及流感引起的发热头痛。', '¥12.0'), " +
                    "('小儿止咳糖浆', '儿童用药', '用于儿童感冒引起的咳嗽。', '¥9.5'), " +
                    "('丁桂儿脐贴', '儿童用药', '用于小儿腹泻、腹痛的辅助治疗。', '¥18.0') " +
                    "ON DUPLICATE KEY UPDATE category = VALUES(category), use_desc = VALUES(use_desc), price = VALUES(price)"
            );
            // 品牌商品名药品（便于用户按日常叫法/品牌名搜索）
            stmt.executeUpdate(
                    "INSERT INTO medicines (name, category, use_desc, price) VALUES " +
                    "('999感冒灵颗粒', '感冒类', '解热镇痛，用于感冒引起的头痛、发热、鼻塞、流涕、咽痛。', '¥15.5'), " +
                    "('泰诺（酚麻美敏片）', '感冒类', '用于缓解感冒及流感引起的发热、头痛、鼻塞、流涕、咳嗽。', '¥16.8'), " +
                    "('新康泰克（美扑伪麻胶囊）', '感冒类', '用于缓解普通感冒及流行性感冒引起的发热、喷嚏、流涕等症状。', '¥18.5'), " +
                    "('白加黑（氨麻苯美片）', '感冒类', '日片不嗜睡、夜片助睡眠，用于缓解感冒引起的多种症状。', '¥17.0'), " +
                    "('感康（复方氨酚烷胺片）', '感冒类', '用于缓解病毒性感冒引起的发热、头痛、鼻塞、咽痛。', '¥14.5'), " +
                    "('快克（复方氨酚烷胺胶囊）', '感冒类', '用于缓解普通感冒或流感引起的发热、头痛等症状。', '¥13.8'), " +
                    "('泰诺林（对乙酰氨基酚缓释片）', '退烧止痛', '用于普通感冒或流感引起的发热，缓解轻中度疼痛。', '¥14.8'), " +
                    "('芬必得（布洛芬缓释胶囊）', '退烧止痛', '用于缓解头痛、关节痛、牙痛等轻中度疼痛及感冒发热。', '¥19.8'), " +
                    "('散利痛（复方对乙酰氨基酚片Ⅱ）', '退烧止痛', '用于头痛、牙痛、神经痛及发热的对症治疗。', '¥16.5'), " +
                    "('吗丁啉（多潘立酮片）', '消化类', '用于消化不良、腹胀、嗳气、恶心、呕吐。', '¥18.6'), " +
                    "('斯达舒（维U颠茄铝胶囊）', '消化类', '用于缓解胃酸过多引起的胃痛、胃灼热感、反酸。', '¥22.0'), " +
                    "('思密达（蒙脱石散）', '消化类', '用于成人及儿童急慢性腹泻，保护肠道黏膜。', '¥17.9'), " +
                    "('江中健胃消食片', '消化类', '用于脾胃虚弱所致的食积、不思饮食、脘腹胀满。', '¥11.5'), " +
                    "('开瑞坦（氯雷他定片）', '过敏类', '用于缓解过敏性鼻炎、慢性荨麻疹等过敏症状。', '¥25.9'), " +
                    "('仙特明（盐酸西替利嗪片）', '过敏类', '用于季节性或常年性过敏性鼻炎、荨麻疹。', '¥23.5'), " +
                    "('京都念慈菴蜜炼川贝枇杷膏', '止咳化痰', '润肺止咳，用于燥咳、咽痛、声音嘶哑。', '¥28.8'), " +
                    "('强力枇杷露', '止咳化痰', '养阴敛肺，止咳祛痰，用于久咳劳嗽。', '¥12.8'), " +
                    "('复方鲜竹沥液', '止咳化痰', '清热化痰，用于痰热咳嗽、痰黄黏稠。', '¥15.6'), " +
                    "('达克宁（硝酸咪康唑乳膏）', '皮肤外用', '用于手足癣、体癣、股癣等真菌感染性皮肤病。', '¥14.9'), " +
                    "('999皮炎平（复方醋酸地塞米松乳膏）', '皮肤外用', '用于局限性瘙痒症、神经性皮炎、接触性皮炎。', '¥10.8'), " +
                    "('钙尔奇D（碳酸钙D3片）', '营养补充', '用于中老年人、孕产妇等的钙和维生素D补充。', '¥36.0'), " +
                    "('善存（多维元素片）', '营养补充', '用于预防和治疗因维生素与矿物质缺乏引起的疾病。', '¥45.0'), " +
                    "('21金维他（多维元素片）', '营养补充', '补充日常多种维生素和矿物质。', '¥29.9'), " +
                    "('络活喜（苯磺酸氨氯地平片）', '慢病类', '用于治疗高血压及心绞痛，需遵医嘱使用。', '¥38.5'), " +
                    "('拜新同（硝苯地平控释片）', '慢病类', '用于高血压、冠心病心绞痛的治疗，需遵医嘱使用。', '¥33.0'), " +
                    "('代文（缬沙坦胶囊）', '慢病类', '用于轻中度原发性高血压，需遵医嘱使用。', '¥42.0'), " +
                    "('格华止（盐酸二甲双胍缓释片）', '慢病类', '用于2型糖尿病的血糖控制，需遵医嘱使用。', '¥28.5'), " +
                    "('拜唐苹（阿卡波糖片）', '慢病类', '用于2型糖尿病餐后血糖控制，需遵医嘱使用。', '¥56.0'), " +
                    "('立普妥（阿托伐他汀钙片）', '慢病类', '用于高胆固醇血症和混合型血脂异常，需遵医嘱使用。', '¥52.0'), " +
                    "('小葵花小儿肺热咳喘口服液', '儿童用药', '用于儿童感冒、支气管炎等引起的咳嗽、痰黄。', '¥24.8') " +
                    "ON DUPLICATE KEY UPDATE category = VALUES(category), use_desc = VALUES(use_desc), price = VALUES(price)"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS diseases (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(200) NOT NULL UNIQUE, " +
                    "tag VARCHAR(100) NOT NULL, " +
                    "category VARCHAR(100) DEFAULT '常见疾病', " +
                    "causes VARCHAR(500), " +
                    "symptoms VARCHAR(500), " +
                    "focus VARCHAR(500), " +
                    "description VARCHAR(500) NOT NULL, " +
                    "views INT DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            migrateColumn(stmt, "diseases", "category", "VARCHAR(100) DEFAULT '常见疾病'");
            migrateColumn(stmt, "diseases", "causes", "VARCHAR(500)");
            migrateColumn(stmt, "diseases", "symptoms", "VARCHAR(500)");
            migrateColumn(stmt, "diseases", "focus", "VARCHAR(500)");
            migrateColumn(stmt, "diseases", "views", "INT DEFAULT 0");
            migrateColumn(stmt, "health_profiles", "phone", "VARCHAR(50)");
            migrateColumn(stmt, "health_profiles", "email", "VARCHAR(200)");
            migrateColumn(stmt, "medicines", "brand", "VARCHAR(100)");
            seedDiseases(conn);
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS feedbacks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "type VARCHAR(50) NOT NULL, " +
                    "content VARCHAR(1000) NOT NULL, " +
                    "contact VARCHAR(200), " +
                    "status VARCHAR(20) DEFAULT '待处理', " +
                    "reply VARCHAR(1000), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS health_profiles (" +
                    "username VARCHAR(100) PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "gender VARCHAR(20), " +
                    "age VARCHAR(20), " +
                    "blood_type VARCHAR(20), " +
                    "height VARCHAR(20), " +
                    "weight VARCHAR(20), " +
                    "allergies VARCHAR(500), " +
                    "chronic VARCHAR(500), " +
                    "notes VARCHAR(500))"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS chat_records (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "content VARCHAR(1000) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS doctors (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "specialty VARCHAR(100) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS consultations (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "patient_username VARCHAR(100) NOT NULL, " +
                    "doctor_username VARCHAR(100) NOT NULL, " +
                    "doctor_name VARCHAR(100), " +
                    "specialty VARCHAR(100), " +
                    "cond_text VARCHAR(2000), " +
                    "status VARCHAR(20) DEFAULT '待回复', " +
                    "reply VARCHAR(2000), " +
                    "patient_read TINYINT DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "replied_at TIMESTAMP NULL)"
            );
            // 家庭药箱：用户自己录入在用的药
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS cabinet_items (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "medicine_name VARCHAR(200) NOT NULL, " +
                    "spec VARCHAR(100) DEFAULT '', " +
                    "quantity INT DEFAULT 1, " +
                    "expire_date VARCHAR(20), " +
                    "note VARCHAR(500), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_cabinet (username, medicine_name, spec))"
            );
            // 用药提醒计划：times 存 "08:00,12:00,20:00"
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS medication_plans (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "cabinet_id INT, " +
                    "medicine_name VARCHAR(200) NOT NULL, " +
                    "dosage VARCHAR(100) DEFAULT '', " +
                    "times VARCHAR(200) NOT NULL, " +
                    "start_date VARCHAR(20), " +
                    "end_date VARCHAR(20), " +
                    "active TINYINT DEFAULT 1, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            // 打卡记录：一个计划一天一个时间点只能打一次
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS medication_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "plan_id INT NOT NULL, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "take_date VARCHAR(20) NOT NULL, " +
                    "slot VARCHAR(10) NOT NULL, " +
                    "taken_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_med_log (plan_id, take_date, slot))"
            );
            // 用药安全规则：成分级禁忌 / 特殊人群提示
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS drug_rules (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "ingredient VARCHAR(100) NOT NULL, " +
                    "rule_type VARCHAR(30) NOT NULL, " +
                    "target VARCHAR(100) DEFAULT '', " +
                    "level VARCHAR(10) DEFAULT 'amber', " +
                    "message VARCHAR(400) NOT NULL, " +
                    "advice VARCHAR(400) DEFAULT '', " +
                    "UNIQUE KEY uk_drug_rule (ingredient, rule_type, target))"
            );
            seedDrugRules(conn);
            // 化验单解读：每次识别存一条，供复诊时对比
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS lab_reports (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "raw_text MEDIUMTEXT, " +
                    "items_json MEDIUMTEXT, " +
                    "result_json MEDIUMTEXT, " +
                    "summary VARCHAR(600) DEFAULT '', " +
                    "level VARCHAR(10) DEFAULT 'green', " +
                    "abnormal_count INT DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "KEY idx_lab_user (username, created_at))"
            );
            // 收藏：药品 / 病种
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS favorites (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "target_type VARCHAR(20) NOT NULL, " +
                    "target_id VARCHAR(200) NOT NULL, " +
                    "title VARCHAR(200) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_favorite (username, target_type, target_id))"
            );
            // 浏览历史：同一目标只留一条，重复浏览刷新时间
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS browse_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "target_type VARCHAR(20) NOT NULL, " +
                    "target_id VARCHAR(200) NOT NULL, " +
                    "title VARCHAR(200) NOT NULL, " +
                    "viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_history (username, target_type, target_id))"
            );
            // 医生开方：用药计划增加来源与下达医生信息
            migrateColumn(stmt, "medication_plans", "doctor_username", "VARCHAR(100) DEFAULT ''");
            migrateColumn(stmt, "medication_plans", "doctor_name", "VARCHAR(100) DEFAULT ''");
            migrateColumn(stmt, "medication_plans", "note", "VARCHAR(500) DEFAULT ''");
            migrateColumn(stmt, "medication_plans", "source", "VARCHAR(20) DEFAULT 'self'");
            migrateColumn(stmt, "medication_plans", "patient_read", "TINYINT DEFAULT 0");
            stmt.executeUpdate(
                    "INSERT INTO doctors (username, password, name, specialty) VALUES " +
                    "('doctor1', '123456', '张医生', '全科'), " +
                    "('doctor2', '123456', '李医生', '呼吸科'), " +
                    "('doctor3', '123456', '王医生', '消化科') " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name), specialty = VALUES(specialty)"
            );
        }
    }

    // 病种种子数据：名称 / 分类 / 标签 / 病因 / 常见症状 / 关注点 / 简介 / 浏览量
    private static final String[][] DISEASE_SEED = {
            {"上呼吸道感染", "呼吸系统疾病", "常见感染", "病毒或细菌感染，受凉、疲劳后抵抗力下降", "鼻塞、流涕、咽痛、低热", "持续高热超过3天或症状加重需就医", "多表现为鼻塞、流涕、咽痛、低热，通常与受凉或病毒感染有关。", "168"},
            {"急性扁桃体炎", "呼吸系统疾病", "咽喉疾病", "细菌或病毒感染，受凉、疲劳后抵抗力下降", "咽痛、发热、吞咽困难", "扁桃体红肿、有脓点", "常见咽痛明显、吞咽困难、口臭，部分伴有发热和淋巴结肿大。", "222"},
            {"过敏性鼻炎", "呼吸系统疾病", "过敏类", "花粉、尘螨等过敏原刺激，遗传过敏体质", "打喷嚏、鼻痒、清水样鼻涕", "季节性反复发作、常伴眼痒", "反复打喷嚏、鼻痒、流清鼻涕，通常在季节变化或接触过敏原时加重。", "143"},
            {"胃炎", "消化系统疾病", "消化类", "幽门螺杆菌感染、饮食不规律、刺激性食物", "上腹隐痛、反酸、嗳气、恶心", "空腹或进食后上腹不适需胃镜检查", "可见上腹隐痛、反酸、嗳气，部分患者伴恶心、食欲下降。", "176"},
            {"急性肠胃炎", "消化系统疾病", "消化类", "进食不洁食物、细菌或病毒感染", "腹痛、腹泻、恶心、呕吐", "脱水、发热、血便需及时就医", "典型表现为腹痛、腹泻、恶心、呕吐，需注意补液和饮食管理。", "198"},
            {"支气管炎", "呼吸系统疾病", "呼吸类", "病毒或细菌感染、长期吸烟、空气污染等", "咳嗽、咳痰、气喘、胸闷", "痰液呈黄绿色、伴发热", "常见咳嗽、痰多、胸闷，可能由感染、刺激物或过敏诱发。", "55"},
            {"流行性感冒", "呼吸系统疾病", "常见感染", "流感病毒感染，冬春季高发，传染性强", "高热、全身酸痛、乏力、咳嗽", "起病急、体温可达39℃以上", "由流感病毒引起，起病急，常伴高热、全身酸痛、乏力，传染性较强。", "205"},
            {"哮喘", "呼吸系统疾病", "呼吸类", "遗传因素、过敏原刺激、呼吸道感染等", "反复发作的喘息、呼吸困难、胸闷、咳嗽", "夜间和清晨症状加重，可闻及哮鸣音", "反复发作的喘息、气促、胸闷或咳嗽，常在夜间或接触过敏原时加重。", "54"},
            {"胃食管反流病", "消化系统疾病", "消化类", "食管下括约肌松弛、肥胖、饮食过饱", "烧心、反酸、胸骨后不适", "餐后或平卧时加重", "胃内容物反流引起烧心、反酸、胸骨后不适，常在餐后或平卧时加重。", "121"},
            {"偏头痛", "神经系统疾病", "神经类", "遗传、内分泌变化、睡眠不足、精神紧张", "单侧搏动性头痛、恶心、畏光", "反复发作，可伴视觉先兆", "反复发作的一侧搏动性头痛，常伴恶心、畏光、畏声。", "89"},
            {"高血压", "心血管疾病", "慢病类", "遗传、高盐饮食、肥胖、长期精神紧张", "头晕、头痛、颈项发紧", "多数起病隐匿，需长期监测管理", "以体循环动脉压升高为主要表现，多数起病隐匿，需长期监测和管理。", "260"},
            {"2型糖尿病", "内分泌疾病", "慢病类", "胰岛素抵抗、肥胖、遗传、缺乏运动", "多饮、多食、多尿、体重下降", "空腹血糖≥7.0mmol/L需规范治疗", "以血糖升高为特征，常见多饮、多食、多尿、体重下降等表现。", "231"},
            {"慢性咽炎", "呼吸系统疾病", "咽喉疾病", "长期用嗓、烟酒刺激、鼻部疾病分泌物刺激", "咽部异物感、干痒、灼热", "晨起刺激性干咳、病程较长", "咽部异物感、干痒、灼热，晨起刺激性干咳，病程较长。", "112"},
            {"中耳炎", "耳鼻喉疾病", "耳鼻喉类", "上呼吸道感染经咽鼓管蔓延、耳部进水", "耳痛、听力下降、耳闷", "儿童多见，可伴发热或耳流脓", "耳痛、听力下降、耳闷，部分伴有发热或耳流脓。", "76"},
            {"结膜炎", "眼科疾病", "眼科类", "细菌或病毒感染、过敏、异物刺激", "眼红、异物感、流泪、分泌物增多", "伴明显痒感多为过敏性结膜炎", "眼红、异物感、流泪、分泌物增多，可伴明显痒感。", "64"},
            {"湿疹", "皮肤疾病", "皮肤类", "皮肤屏障受损、过敏体质、环境潮湿", "红斑、丘疹、瘙痒、脱屑", "反复发作，搔抓后易渗出感染", "皮肤红斑、丘疹、瘙痒，反复发作，可伴渗出或脱屑。", "133"},
            {"颈椎病", "骨关节疾病", "骨关节类", "长期低头、姿势不良、椎间盘退变", "颈肩酸痛、僵硬、上肢麻木", "伴头晕或手麻需及时就诊", "颈肩部酸痛、僵硬，可伴上肢麻木、头晕等。", "158"},
            {"泌尿系感染", "泌尿系统疾病", "泌尿类", "细菌逆行感染、饮水少、习惯性憋尿", "尿频、尿急、尿痛", "伴发热腰痛提示上尿路感染", "尿频、尿急、尿痛，可伴下腹不适或发热。", "97"},
            {"肺炎", "呼吸系统疾病", "呼吸类", "细菌、病毒或支原体感染", "发热、咳嗽、咳痰、胸痛", "气促、血氧下降需急诊处理", "发热、咳嗽、咳痰，可伴胸痛、气促，需及时就医。", "187"},
            {"失眠症", "精神心理疾病", "神经类", "精神压力、作息紊乱、焦虑抑郁情绪", "入睡困难、易醒、早醒、日间困倦", "持续超过一个月需专业评估", "入睡困难、易醒、早醒或睡眠质量差，影响日间状态。", "174"},
            {"焦虑症", "精神心理疾病", "精神心理类", "遗传因素、性格因素、应激事件等", "过度担忧、紧张不安、心悸", "难以控制的担心", "以持续性的过度担忧和紧张不安为主要表现，常伴心悸、坐立不安等躯体症状。", "237"},
            {"强迫症", "精神心理疾病", "精神心理类", "遗传因素、神经生化异常、心理因素等", "反复出现的强迫思维和强迫行为", "明知不必要但无法控制", "以反复出现的强迫观念与强迫行为为特征，常因无法控制而明显影响生活。", "146"},
            {"慢性阻塞性肺病", "呼吸系统疾病", "呼吸类", "长期吸烟、空气污染、职业粉尘等", "慢性咳嗽、咳痰、进行性呼吸困难", "桶状胸、活动后气促明显", "以持续气流受限为特征，常见慢性咳嗽、咳痰和活动后气促，需长期规范管理。", "165"},
            {"冠心病", "心血管疾病", "慢病类", "动脉粥样硬化、高血压、糖尿病、吸烟等", "心绞痛、胸闷、气短", "劳累后发作，休息或含服硝酸甘油缓解", "冠状动脉粥样硬化导致心肌供血不足，常见胸闷、胸痛，需规范抗血小板与调脂治疗。", "227"},
            {"胃溃疡", "消化系统疾病", "消化类", "幽门螺杆菌感染、长期服用非甾体抗炎药、精神紧张等", "上腹疼痛、反酸、嗳气、恶心", "餐后痛为主，疼痛有规律性", "胃黏膜缺损超过黏膜肌层，典型表现为规律性上腹痛，需根除幽门螺杆菌并规范抑酸。", "130"},
            {"肠易激综合征", "消化系统疾病", "消化类", "肠道功能紊乱、精神心理因素、饮食不当等", "腹痛、腹胀、排便习惯改变", "症状与排便相关，排便后缓解", "以腹痛伴排便习惯改变为特征的功能性肠病，需调整饮食与情绪管理。", "151"},
            {"高脂血症", "心血管疾病", "慢病类", "高脂高糖饮食、肥胖、缺乏运动、遗传", "多无明显症状，体检发现血脂升高", "合并高血压糖尿病心血管风险更高", "血脂水平异常升高，早期常无症状，长期可加速动脉粥样硬化。", "118"},
            {"骨质疏松", "骨关节疾病", "骨关节类", "年龄增长、绝经、钙与维生素D摄入不足", "腰背疼痛、身高变矮、易骨折", "轻微外力即骨折需骨密度评估", "骨量减少、骨微结构破坏导致骨脆性增加，中老年人群高发。", "72"},
            {"荨麻疹", "皮肤疾病", "皮肤类", "食物或药物过敏、感染、冷热刺激", "风团、剧烈瘙痒、时起时消", "伴呼吸困难或喉头水肿需急诊", "皮肤黏膜小血管扩张渗出形成风团，常反复发作，需寻找并规避诱因。", "105"},
            {"痛风", "骨关节疾病", "骨关节类", "高嘌呤饮食、饮酒、尿酸排泄减少", "关节红肿热痛，多见于足大趾", "夜间突发剧痛、易反复发作", "尿酸盐结晶沉积关节引起急性炎症，需控制嘌呤摄入并规范降尿酸。", "141"},
            {"缺铁性贫血", "血液系统疾病", "慢病类", "铁摄入不足、慢性失血、吸收障碍", "乏力、头晕、面色苍白、心悸", "女性月经量多者高发", "体内铁储备不足导致血红蛋白合成减少，表现为乏力、头晕与面色苍白。", "88"},
            {"抑郁症", "精神心理疾病", "精神心理类", "遗传、神经递质紊乱、重大生活事件", "情绪低落、兴趣减退、睡眠障碍", "持续两周以上需专业评估", "以显著而持久的情绪低落为核心表现，可伴兴趣减退、精力下降和睡眠障碍。", "219"}
    };

    private static void migrateColumn(Statement stmt, String table, String column, String definition) {
        try {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // 字段已存在
        }
    }

    private static void seedDiseases(Connection conn) {
        String sql = "INSERT INTO diseases (name, tag, category, causes, symptoms, focus, description, views) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE tag = VALUES(tag), category = VALUES(category), " +
                "causes = VALUES(causes), symptoms = VALUES(symptoms), focus = VALUES(focus), " +
                "description = VALUES(description), views = IF(views = 0, VALUES(views), views)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String[] row : DISEASE_SEED) {
                stmt.setString(1, row[0]);
                stmt.setString(2, row[2]);
                stmt.setString(3, row[1]);
                stmt.setString(4, row[3]);
                stmt.setString(5, row[4]);
                stmt.setString(6, row[5]);
                stmt.setString(7, row[6]);
                stmt.setInt(8, Integer.parseInt(row[7]));
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("病种种子数据写入失败：" + e.getMessage());
        }
    }

    // 用药安全规则：成分 / 类型(contraindication|population) / 目标(疾病或人群) / 级别 / 提示 / 建议
    private static final String[][] DRUG_RULE_SEED = {
            {"布洛芬", "contraindication", "高血压", "amber", "布洛芬可能升高血压，并减弱降压药的降压效果", "长期或大剂量使用前先咨询医生，服药期间注意监测血压"},
            {"布洛芬", "contraindication", "胃溃疡", "red", "布洛芬会损伤胃黏膜，胃溃疡患者使用可能诱发出血", "避免自行服用，需在医生指导下加用胃黏膜保护剂"},
            {"布洛芬", "contraindication", "胃炎", "amber", "布洛芬对胃黏膜有刺激，可能加重胃部不适", "饭后服用，若出现黑便、上腹剧痛立即就医"},
            {"布洛芬", "contraindication", "哮喘", "amber", "部分哮喘患者服用布洛芬可能诱发哮喘发作", "首次服用后留意有无喘息、胸闷，有异常立即停用"},
            {"布洛芬", "contraindication", "冠心病", "amber", "长期使用非甾体抗炎药可能增加心血管事件风险", "仅在必要时短期使用，并告知医生心血管病史"},
            {"布洛芬", "contraindication", "肾病", "red", "布洛芬可能减少肾血流，加重肾功能损害", "肾功能不全者应避免使用，改用对乙酰氨基酚前也需咨询医生"},
            {"阿司匹林", "contraindication", "胃溃疡", "red", "阿司匹林抑制胃黏膜保护物质合成，溃疡患者出血风险高", "必须使用者需联用抑酸药，并观察有无黑便"},
            {"阿司匹林", "contraindication", "哮喘", "amber", "阿司匹林可能诱发支气管痉挛", "有阿司匹林哮喘史者禁用"},
            {"阿司匹林", "contraindication", "痛风", "amber", "小剂量阿司匹林可能影响尿酸排泄", "痛风患者长期服用需监测血尿酸"},
            {"对乙酰氨基酚", "contraindication", "肝病", "red", "对乙酰氨基酚经肝脏代谢，肝功能不全者易蓄积中毒", "避免使用或减量，并严格禁酒"},
            {"对乙酰氨基酚", "contraindication", "肝硬化", "red", "肝硬化患者代谢能力下降，常规剂量也可能造成肝损伤", "请由医生评估后再用"},
            {"感冒灵", "contraindication", "高血压", "amber", "复方感冒药常含伪麻黄碱等成分，可能升高血压", "高血压患者选药前先看成分表，避开含伪麻黄碱的品种"},
            {"氨酚黄那敏", "contraindication", "高血压", "amber", "含伪麻黄碱成分，可能使血压升高", "改用不含伪麻黄碱的感冒药"},
            {"二甲双胍", "contraindication", "肾病", "red", "肾功能不全时二甲双胍排泄减慢，有乳酸酸中毒风险", "需定期查肾功能，eGFR 明显下降时应调整方案"},
            {"二甲双胍", "contraindication", "肝病", "amber", "肝功能受损会增加乳酸酸中毒风险", "用药期间定期复查肝功能"},
            {"左氧氟沙星", "contraindication", "癫痫", "amber", "喹诺酮类可能降低癫痫发作阈值", "有癫痫史者慎用，出现异常及时停药"},
            {"奥美拉唑", "contraindication", "骨质疏松", "amber", "长期抑酸可能影响钙吸收，加重骨量流失", "长期服用建议补充钙与维生素D"},
            {"阿托伐他汀", "contraindication", "肝病", "amber", "他汀类经肝脏代谢，肝病患者需监测转氨酶", "用药初期及调整剂量后复查肝功能"},
            {"布洛芬", "population", "老年人", "amber", "老年人胃黏膜与肾功能储备下降，非甾体抗炎药风险更高", "优先选择对乙酰氨基酚，且用最低有效剂量、最短疗程"},
            {"阿司匹林", "population", "儿童", "red", "儿童病毒感染期使用阿司匹林可能诱发瑞氏综合征", "18 岁以下发热患儿不要自行使用阿司匹林"},
            {"感冒灵", "population", "儿童", "amber", "复方感冒药成分复杂，低龄儿童不建议自行服用", "6 岁以下儿童用药请先咨询儿科医生"},
            {"左氧氟沙星", "population", "儿童", "red", "喹诺酮类可能影响未成年人的软骨发育", "18 岁以下一般不使用"},
            {"左氧氟沙星", "population", "孕妇", "red", "孕期禁用喹诺酮类抗菌药", "请由产科医生更换安全的抗菌药"},
            {"布洛芬", "population", "孕妇", "amber", "孕晚期使用布洛芬可能影响胎儿循环", "孕期用药请先咨询产科医生，首选对乙酰氨基酚并遵医嘱"}
    };

    private static void seedDrugRules(Connection conn) {
        String sql = "INSERT INTO drug_rules (ingredient, rule_type, target, level, message, advice) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE level = VALUES(level), message = VALUES(message), advice = VALUES(advice)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String[] row : DRUG_RULE_SEED) {
                stmt.setString(1, row[0]);
                stmt.setString(2, row[1]);
                stmt.setString(3, row[2]);
                stmt.setString(4, row[3]);
                stmt.setString(5, row[4]);
                stmt.setString(6, row[5]);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("用药规则写入失败：" + e.getMessage());
        }
    }

    private static boolean registerUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM users WHERE username = ?");
             PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")) {
            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }

            insertStmt.setString(1, username);
            insertStmt.setString(2, password);
            return insertStmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean validateLogin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM users WHERE username = ? AND password = ?")) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return "admin".equals(username) && "123456".equals(password);
        }
    }

    private static List<Map<String, Object>> loadMedicinesFromDb(String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT name, category, use_desc AS `use`, price, brand FROM medicines";
        List<String> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " WHERE name LIKE ? OR category LIKE ? OR use_desc LIKE ? OR price LIKE ? OR brand LIKE ?";
            String value = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(value);
            }
        }

        sql += " ORDER BY id";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String category = rs.getString("category");
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", name);
                    item.put("category", category);
                    item.put("use", rs.getString("use"));
                    item.put("price", rs.getString("price"));
                    String brand = rs.getString("brand");
                    item.put("brand", brand == null || brand.isBlank() ? inferBrand(name, category) : brand);
                    item.put("kind", medicineKind(name, category));
                    result.add(item);
                }
            }
        } catch (SQLException e) {
            return MEDICINE_DATA;
        }

        // 有搜索词但无匹配时返回空列表（由前端提示"没有找到"），仅在数据库异常时才回退内置数据
        return result;
    }

    // 药品品牌：常见商品名/厂牌推断，未命中时按分类给出默认厂牌
    private static String inferBrand(String name, String category) {
        if (name == null) return "国药牌";
        String[][] rules = {
                {"999感冒灵", "999牌"}, {"999皮炎平", "999牌"}, {"感冒灵", "999牌"},
                {"阿莫西林", "哈药牌"}, {"奥美拉唑", "洛赛克牌"}, {"板蓝根", "白云山牌"},
                {"连花清瘟", "以岭牌"}, {"泰诺", "泰诺牌"}, {"芬必得", "芬必得牌"},
                {"布洛芬", "中美史克"}, {"对乙酰氨基酚", "泰诺林"}, {"头孢", "白云山牌"},
                {"阿奇霉素", "希舒美牌"}, {"蒙脱石", "思密达牌"}, {"多潘立酮", "吗丁啉牌"},
                {"氯雷他定", "开瑞坦牌"}, {"西替利嗪", "仙特明牌"}, {"二甲双胍", "格华止牌"},
                {"阿托伐他汀", "立普妥牌"}, {"氨氯地平", "络活喜牌"}, {"硝苯地平", "拜新同牌"},
                {"缬沙坦", "代文牌"}, {"阿卡波糖", "拜唐苹牌"}, {"川贝枇杷", "念慈菴牌"},
                {"六味地黄丸", "同仁堂"}, {"逍遥丸", "同仁堂"}, {"云南白药", "云南白药牌"},
                {"维生素C", "养生堂牌"}, {"钙尔奇", "钙尔奇牌"}, {"善存", "善存牌"},
                {"健胃消食", "江中牌"}, {"新康泰克", "中美史克"}, {"白加黑", "白加黑牌"},
                {"感康", "感康牌"}, {"快克", "快克牌"}, {"散利痛", "散利痛牌"},
                {"达克宁", "达克宁牌"}, {"红霉素软膏", "白云山牌"}, {"碘伏", "利尔康牌"},
                {"创可贴", "邦迪牌"}, {"金嗓子", "金嗓子牌"}, {"西瓜霜", "三金牌"},
                {"藿香正气", "太极牌"}, {"复方丹参滴丸", "天士力牌"}, {"稳心颗粒", "步长牌"}
        };
        for (String[] rule : rules) {
            if (name.contains(rule[0])) return rule[1];
        }
        return switch (category == null ? "" : category) {
            case "慢病类" -> "国药牌";
            case "中成药" -> "同仁堂";
            case "感冒类", "退烧止痛", "止咳类", "止咳化痰" -> "999牌";
            case "抗感染", "抗病毒" -> "哈药牌";
            case "消化类", "肠胃调理" -> "江中牌";
            case "皮肤外用" -> "白云山牌";
            case "营养补充" -> "善存牌";
            case "儿童用药" -> "小葵花牌";
            default -> "国药牌";
        };
    }

    // 中成药判定：用于列表中的「中药 / 西药」角标
    private static String medicineKind(String name, String category) {
        if (name == null) return "西药";
        if ("中成药".equals(category)) return "中药";
        String[] chineseKeywords = {
                "板蓝根", "感冒灵", "连花清瘟", "双黄连", "小柴胡", "银翘", "荆防", "金花清感",
                "蓝芩", "蒲地蓝", "藿香", "保和", "肠炎宁", "黄连素", "川贝", "枇杷", "急支",
                "强力", "鲜竹沥", "云南白药", "红花油", "风油精", "念慈菴", "牛黄", "清开灵",
                "健胃消食", "丹参滴丸", "稳心", "六味地黄", "逍遥", "归脾", "补中益气", "金嗓子",
                "西瓜霜", "咽炎片", "开喉剑", "丁桂", "维C银翘", "苏黄", "蜜炼"
        };
        for (String keyword : chineseKeywords) {
            if (name.contains(keyword)) return "中药";
        }
        return "西药";
    }

    private static List<Map<String, Object>> loadDiseasesFromDb(String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT name, tag, category, causes, symptoms, focus, description, views, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created FROM diseases";
        List<String> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " WHERE name LIKE ? OR tag LIKE ? OR description LIKE ? OR category LIKE ? " +
                    "OR causes LIKE ? OR symptoms LIKE ?";
            String value = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) {
                params.add(value);
            }
        }

        sql += " ORDER BY views DESC, id";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", rs.getString("name"));
                    item.put("tag", rs.getString("tag"));
                    item.put("category", rs.getString("category"));
                    item.put("causes", rs.getString("causes"));
                    item.put("symptoms", rs.getString("symptoms"));
                    item.put("focus", rs.getString("focus"));
                    item.put("description", rs.getString("description"));
                    item.put("views", rs.getInt("views"));
                    item.put("createdAt", rs.getString("created"));
                    result.add(item);
                }
            }
        } catch (SQLException e) {
            return DISEASE_DATA;
        }

        return result;
    }

    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static String readQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void handleDiseases(HttpExchange exchange) throws IOException {
        String keyword = readQueryParam(exchange, "keyword");
        sendJson(exchange, loadDiseasesFromDb(keyword));
    }

    private static void handleDiseaseView(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String name = str(request.get("name")).trim();
        int views = 0;
        try (Connection conn = getConnection()) {
            try (PreparedStatement update = conn.prepareStatement("UPDATE diseases SET views = views + 1 WHERE name = ?")) {
                update.setString(1, name);
                update.executeUpdate();
            }
            try (PreparedStatement query = conn.prepareStatement("SELECT views FROM diseases WHERE name = ?")) {
                query.setString(1, name);
                try (ResultSet rs = query.executeQuery()) {
                    if (rs.next()) views = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "浏览量更新失败"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "views", views));
    }

    private static final String[] DOSAGE_SUFFIXES = {
            "缓释胶囊", "缓释片", "分散片", "肠溶片", "肠溶胶囊", "控释片", "咀嚼片", "泡腾片",
            "混悬液", "干混悬剂", "口服液", "软胶囊", "滴眼液", "滴鼻液", "喷雾剂", "气雾剂",
            "含片", "糖浆", "乳膏", "软膏", "凝胶", "洗剂", "滴剂", "胶囊", "颗粒", "片", "散",
            "丸", "膏", "贴", "液"
    };

    private static String medicineCoreName(String name) {
        if (name == null) return "";
        String core = name.replaceAll("[（(].*?[）)]", "");
        for (String suffix : DOSAGE_SUFFIXES) {
            if (core.endsWith(suffix) && core.length() > suffix.length()) {
                core = core.substring(0, core.length() - suffix.length());
                break;
            }
        }
        return core.trim();
    }

    private static List<Map<String, Object>> matchMedicines(String text) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String normalized = text.replaceAll("[\\s，。、；：！？,.;:!?\"'“”]+", "");

        List<Map<String, Object>> all = loadMedicinesFromDb(null);
        List<Map<String, Object>> scored = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();

        for (Map<String, Object> item : all) {
            String name = str(item.get("name"));
            String core = medicineCoreName(name);
            int score = 0;
            List<String> hits = new ArrayList<>();

            if (!name.isEmpty() && normalized.contains(name)) {
                score += 120;
                hits.add(name);
            }
            if (core.length() >= 2 && normalized.contains(core)) {
                score += 40 + core.length() * 6;
                hits.add(core);
            } else if (core.length() >= 3) {
                for (int len = Math.min(core.length(), 5); len >= 3; len--) {
                    for (int i = 0; i + len <= core.length(); i++) {
                        String fragment = core.substring(i, i + len);
                        if (normalized.contains(fragment)) {
                            score += len * 4;
                            if (!hits.contains(fragment)) hits.add(fragment);
                        }
                    }
                }
            }
            String category = str(item.get("category"));
            if (!category.isEmpty() && normalized.contains(category)) score += 6;
            String brand = str(item.get("brand"));
            if (!brand.isEmpty() && normalized.contains(brand.replace("牌", ""))) score += 8;

            if (score > 0) {
                Map<String, Object> candidate = new LinkedHashMap<>(item);
                candidate.put("score", Math.min(score, 99));
                candidate.put("matched", hits);
                scored.add(candidate);
                scores.add(score);
            }
        }

        // 按得分排序（简单插入排序，数据量小）
        for (int i = 1; i < scored.size(); i++) {
            Map<String, Object> current = scored.get(i);
            int currentScore = scores.get(i);
            int j = i - 1;
            while (j >= 0 && scores.get(j) < currentScore) {
                scored.set(j + 1, scored.get(j));
                scores.set(j + 1, scores.get(j));
                j--;
            }
            scored.set(j + 1, current);
            scores.set(j + 1, currentScore);
        }

        for (int i = 0; i < Math.min(scored.size(), 8); i++) {
            result.add(scored.get(i));
        }
        return result;
    }

    private static void handleIdentify(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String text = str(request.get("text")).trim();
        if (text.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "请先输入或识别药盒文字"));
            return;
        }
        List<Map<String, Object>> candidates = matchMedicines(text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("text", text);
        payload.put("candidates", candidates);
        payload.put("message", candidates.isEmpty() ? "未匹配到药品，可减少关键词后重试" : "已找到 " + candidates.size() + " 个候选药品");
        sendJson(exchange, payload);
    }

    /** 已配置可用的 OCR 引擎，按优先级排列 */
    private static List<String> availableOcrEngines() {
        boolean baiduReady = !cfg("BAIDU_OCR_API_KEY", "").isBlank() && !cfg("BAIDU_OCR_SECRET_KEY", "").isBlank();
        boolean tencentReady = !cfg("TENCENT_OCR_SECRET_ID", "").isBlank() && !cfg("TENCENT_OCR_SECRET_KEY", "").isBlank();
        boolean windowsReady = localOcrAvailable();
        String prefer = cfg("OCR_ENGINE", "auto").toLowerCase();
        List<String> engines = new ArrayList<>();
        if ("baidu".equals(prefer)) {
            if (baiduReady) engines.add("baidu");
        } else if ("tencent".equals(prefer)) {
            if (tencentReady) engines.add("tencent");
        } else if ("local".equals(prefer)) {
            if (windowsReady) engines.add("local");
        } else {
            if (baiduReady) engines.add("baidu");
            if (tencentReady) engines.add("tencent");
            if (windowsReady) engines.add("local");
        }
        return engines;
    }

    /** 本地离线识别：依赖 Windows 自带的 OCR 组件，无需任何密钥 */
    private static boolean localOcrAvailable() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        return !powerShellPath().isBlank() && Files.exists(Path.of("tools", "ocr_win.ps1"));
    }

    private static String powerShellPath() {
        for (String candidate : new String[]{
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                "C:\\Windows\\SysWOW64\\WindowsPowerShell\\v1.0\\powershell.exe"}) {
            if (Files.exists(Path.of(candidate))) return candidate;
        }
        return "";
    }

    private static String engineLabel(String engine) {
        switch (engine) {
            case "tencent": return "腾讯云 OCR";
            case "local": return "本地离线识别";
            default: return "百度 OCR";
        }
    }

    /** OCR 引擎状态：前端据此提示用户"能不能真识别" */
    private static void handleOcrStatus(HttpExchange exchange) throws IOException {
        List<String> engines = availableOcrEngines();
        boolean ready = !engines.isEmpty();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("ready", ready);
        payload.put("engine", ready ? engines.get(0) + "-ocr" : "local-filename");
        payload.put("engines", engines);
        String primary = ready ? engines.get(0) : "";
        String backup = engines.size() > 1 ? "（备用：" + engineLabel(engines.get(1)) + "）" : "";
        payload.put("message", ready
                ? ("local".equals(primary)
                        ? "正在使用系统自带的离线识别，无需密钥" + backup
                        : "已启用" + engineLabel(primary) + backup + "，可直接识别药盒文字")
                : "当前没有可用的识别引擎：可填写百度 / 腾讯云密钥，或确认系统已安装中文 OCR 语言包");
        sendJson(exchange, payload);
    }

    private static void handleOcr(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String image = str(request.get("image")).trim();
        String fileName = str(request.get("fileName")).trim();

        List<String> engines = availableOcrEngines();
        boolean configured = !engines.isEmpty();
        String engineError = "";

        if (image.isBlank()) {
            engineError = "未收到图片数据";
        } else if (!configured) {
            engineError = "未配置 OCR 密钥";
        } else {
            String base64 = normalizeBase64(image);
            double megabytes = base64.length() * 0.75d / 1024d / 1024d;
            if (megabytes > 4d) {
                engineError = String.format("图片约 %.1fMB，超过 OCR 接口 4MB 限制，请换更小的照片", megabytes);
            } else {
                List<String> errors = new ArrayList<>();
                for (String engine : engines) {
                    try {
                        String[] result = runOcrEngine(engine, base64);
                        if (!result[1].isBlank()) {
                            errors.add(engineLabel(engine) + "：" + result[1]);
                            continue;
                        }
                        String words = result[0];
                        if (words.isBlank()) {
                            errors.add(engineLabel(engine) + "：图中未识别到文字");
                            continue;
                        }
                        Map<String, Object> ok = new LinkedHashMap<>();
                        ok.put("success", true);
                        ok.put("engine", engine + "-ocr");
                        ok.put("text", words);
                        ok.put("message", "识别完成（" + engineLabel(engine) + "），共 " + words.split("\n").length + " 行文字");
                        sendJson(exchange, ok);
                        return;
                    } catch (Exception e) {
                        errors.add(engineLabel(engine) + "：" + e.getMessage());
                    }
                }
                engineError = String.join("；", errors);
            }
        }

        // 兜底：从文件名中匹配已知药品（仅作提示，不代表真的识别成功）
        String guess = "";
        if (!fileName.isBlank()) {
            String normalized = fileName.replaceAll("\\.[a-zA-Z0-9]+$", "");
            for (Map<String, Object> candidate : matchMedicines(normalized)) {
                if (Integer.parseInt(String.valueOf(candidate.get("score"))) >= 60) {
                    guess = str(candidate.get("name"));
                    break;
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (!guess.isBlank()) {
            payload.put("success", true);
            payload.put("engine", "local-filename");
            payload.put("text", guess);
            payload.put("message", "未能识别图片文字（" + engineError + "），已按文件名猜测为「" + guess + "」，请核对后手动修正");
        } else {
            payload.put("success", false);
            payload.put("engine", configured ? engines.get(0) + "-ocr" : "none");
            payload.put("text", "");
            payload.put("message", configured
                    ? engineError + "。可手动输入药盒文字后点【用文本检索】"
                    : "没有可用的识别引擎：请确认系统已安装中文 OCR 语言包，或在 config.properties 填写百度 / 腾讯云密钥；也可手动输入药盒文字后检索");
        }
        sendJson(exchange, payload);
    }

    /** 去掉 data:image/xxx;base64, 前缀以及换行空白 */
    private static String normalizeBase64(String image) {
        String value = image;
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            value = value.substring(comma + 1);
        }
        return value.replaceAll("\\s+", "");
    }

    /** 按引擎名调用对应识别实现，返回 [文字, 错误信息] */
    private static String[] runOcrEngine(String engine, String base64) throws IOException {
        switch (engine) {
            case "tencent": return tencentGeneralOcr(base64);
            case "local": return localOcr(base64);
            default: return baiduOcr(base64);
        }
    }

    /**
     * 本地离线识别：经由系统自带的 Windows.Media.Ocr 完成，不需要联网和密钥。
     * 识别前会做预处理（小图放大 + 灰度增强），否则药盒上的小字认不出来。
     */
    private static String[] localOcr(String base64) throws IOException {
        String powerShell = powerShellPath();
        Path script = Path.of("tools", "ocr_win.ps1").toAbsolutePath();
        if (powerShell.isBlank() || !Files.exists(script)) {
            return new String[]{"", "当前环境不支持本地识别（需要 Windows 与 tools/ocr_win.ps1）"};
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return new String[]{"", "图片数据不是有效的 base64"};
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
        if (source == null) {
            return new String[]{"", "图片格式无法解析，请使用 JPG / PNG"};
        }
        Path tempImage = Files.createTempFile("medicine-ocr-", ".png");
        try {
            ImageIO.write(prepareForOcr(source), "png", tempImage.toFile());

            ProcessBuilder builder = new ProcessBuilder(
                    powerShell, "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(), "-ImagePath", tempImage.toAbsolutePath().toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished;
            try {
                finished = process.waitFor(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return new String[]{"", "本地识别被中断"};
            }
            if (!finished) {
                process.destroyForcibly();
                return new String[]{"", "本地识别超时"};
            }

            int begin = output.indexOf("TEXT_BEGIN");
            int end = output.indexOf("TEXT_END");
            if (begin < 0 || end < begin) {
                if (output.contains("ERR_NO_ENGINE")) {
                    return new String[]{"", "系统未安装中文 OCR 语言包，请在「Windows 设置 - 时间和语言 - 语言」中为中文添加光学字符识别"};
                }
                String detail = output.replace("\r", "").replace("\n", " ").trim();
                if (detail.length() > 120) detail = detail.substring(0, 120);
                return new String[]{"", detail.isBlank() ? "本地识别未返回结果" : detail};
            }
            String text = output.substring(begin + "TEXT_BEGIN".length(), end)
                    .replace("\r", "")
                    .trim();
            return new String[]{text, ""};
        } finally {
            try {
                Files.deleteIfExists(tempImage);
            } catch (IOException ignored) {
                // 临时文件清理失败不影响识别结果
            }
        }
    }

    /** 统一成最长边 1600px；小图额外放大并做灰度增强，明显提升药盒小字的识别率 */
    private static BufferedImage prepareForOcr(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        double scale = longest < 1000
                ? Math.min(3.0d, 1600.0d / longest)
                : Math.min(1.0d, 1600.0d / longest);

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        if (scale <= 1.0d) return canvas;

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = canvas.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int gray = (int) (0.299d * r + 0.587d * g + 0.114d * b);
                int enhanced = Math.max(0, Math.min(255, (gray - 128) * 3 / 2 + 128));
                canvas.setRGB(x, y, (enhanced << 16) | (enhanced << 8) | enhanced);
            }
        }
        return canvas;
    }

    /** 百度引擎单次识别，返回 [文字, 错误信息] */
    private static String[] baiduOcr(String base64) throws IOException {
        String token = baiduAccessToken(cfg("BAIDU_OCR_API_KEY", ""), cfg("BAIDU_OCR_SECRET_KEY", ""), false);
        if (token.isBlank()) {
            return new String[]{"", "获取 access_token 失败，请检查 API Key / Secret Key 是否正确"};
        }
        return baiduGeneralOcrChecked(token, base64);
    }

    /** 腾讯云通用印刷体识别（TC3-HMAC-SHA256 签名），返回 [文字, 错误信息] */
    private static String[] tencentGeneralOcr(String base64) throws IOException {
        String secretId = cfg("TENCENT_OCR_SECRET_ID", "");
        String secretKey = cfg("TENCENT_OCR_SECRET_KEY", "");
        String region = cfg("TENCENT_OCR_REGION", "ap-guangzhou");
        String host = "ocr.tencentcloudapi.com";
        String service = "ocr";
        long timestamp = System.currentTimeMillis() / 1000;
        String body = "{\"ImageBase64\":\"" + base64 + "\"}";

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = dateFormat.format(new Date(timestamp * 1000L));

        String canonicalRequest = "POST\n/\n\n"
                + "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n\n"
                + "content-type;host\n"
                + sha256Hex(body);
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));
        String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=content-type;host, Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-TC-Action", "GeneralBasicOCR");
        headers.put("X-TC-Version", "2018-11-19");
        headers.put("X-TC-Timestamp", String.valueOf(timestamp));
        headers.put("X-TC-Region", region);

        String response = httpPostJson("https://" + host, body, headers);
        if (response.isBlank()) return new String[]{"", "接口无响应"};
        if (response.contains("\"Error\"")) {
            String code = jsonStringField(response, "Code");
            String message = jsonStringField(response, "Message");
            return new String[]{"", code + " " + message};
        }
        return new String[]{collectJsonFieldValues(response, "DetectedText"), ""};
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用：" + e.getMessage());
        }
    }

    private static byte[] hmacSha256(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 不可用：" + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static String httpPostJson(String url, String json, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    private static String baiduAccessToken(String apiKey, String secretKey, boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        if (!forceRefresh && !ocrToken.isBlank() && now - ocrTokenTime < 25 * 60 * 1000L) {
            return ocrToken;
        }
        String url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id="
                + java.net.URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&client_secret=" + java.net.URLEncoder.encode(secretKey, StandardCharsets.UTF_8);
        String response = httpGet(url);
        int index = response.indexOf("\"access_token\":\"");
        if (index < 0) {
            index = response.indexOf("\"access_token\": \"");
            if (index < 0) return "";
            index += 1;
        }
        int start = response.indexOf(':', index) + 1;
        while (start < response.length() && response.charAt(start) == ' ') start++;
        if (start < response.length() && response.charAt(start) == '"') start++;
        int end = response.indexOf('"', start);
        ocrToken = end < 0 ? "" : response.substring(start, end);
        ocrTokenTime = now;
        return ocrToken;
    }

    /** 返回 [识别文字, 错误信息]，错误信息为空表示成功 */
    private static String[] baiduGeneralOcrChecked(String token, String base64) throws IOException {
        String body = "image=" + java.net.URLEncoder.encode(base64, StandardCharsets.UTF_8) + "&language_type=CHN_ENG&detect_direction=true";
        String response = httpPostForm("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=" + token, body);
        if (response.isBlank()) {
            return new String[]{"", "OCR 接口无响应"};
        }
        if (response.contains("\"error_code\"")) {
            String code = jsonStringField(response, "error_code");
            String msg = jsonStringField(response, "error_msg");
            if ("110".equals(code) || "111".equals(code)) {
                ocrToken = "";
                ocrTokenTime = 0L;
                return new String[]{"", "access_token 已失效（" + msg + "），请重新点一次识别"};
            }
            return new String[]{"", code + " " + msg};
        }
        return new String[]{collectJsonFieldValues(response, "words"), ""};
    }

    /** 依次取出 JSON 中某个字符串字段的所有取值（用于 OCR 的多行文字），按行拼接 */
    private static String collectJsonFieldValues(String response, String field) {
        String needle = "\"" + field + "\"";
        StringBuilder output = new StringBuilder();
        int cursor = 0;
        while (true) {
            int index = response.indexOf(needle, cursor);
            if (index < 0) break;
            int start = response.indexOf(':', index + needle.length());
            if (start < 0) break;
            start++;
            while (start < response.length() && response.charAt(start) == ' ') start++;
            if (start >= response.length() || response.charAt(start) != '"') {
                cursor = start;
                continue;
            }
            start++;
            StringBuilder text = new StringBuilder();
            int i = start;
            while (i < response.length() && response.charAt(i) != '"') {
                char current = response.charAt(i);
                if (current == '\\' && i + 1 < response.length()) {
                    char next = response.charAt(i + 1);
                    if (next == 'n') {
                        text.append('\n');
                        i += 2;
                        continue;
                    }
                    if (next == 'u' && i + 5 < response.length()) {
                        try {
                            text.append((char) Integer.parseInt(response.substring(i + 2, i + 6), 16));
                            i += 6;
                            continue;
                        } catch (NumberFormatException ignored) {
                            // 非法转义，按普通字符处理
                        }
                    }
                    text.append(next);
                    i += 2;
                    continue;
                }
                text.append(current);
                i++;
            }
            if (text.length() > 0) {
                if (output.length() > 0) output.append('\n');
                output.append(text);
            }
            cursor = i + 1;
        }
        return output.toString();
    }

    private static String jsonStringField(String response, String field) {
        int index = response.indexOf("\"" + field + "\"");
        if (index < 0) return "";
        int start = response.indexOf(':', index) + 1;
        while (start < response.length() && (response.charAt(start) == ' ' || response.charAt(start) == '"')) start++;
        int end = start;
        while (end < response.length() && response.charAt(end) != '"' && response.charAt(end) != ',' && response.charAt(end) != '}') end++;
        return response.substring(start, end).trim();
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(10000);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) return "";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    private static String httpPostForm(String url, String form) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(form.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) return "";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    private static void handleSearch(HttpExchange exchange) throws IOException {
        String query = readQueryParam(exchange, "q");
        List<Map<String, Object>> diseases = query == null || query.isBlank()
                ? new ArrayList<>() : loadDiseasesFromDb(query);
        List<Map<String, Object>> medicines = query == null || query.isBlank()
                ? new ArrayList<>() : loadMedicinesFromDb(query);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query == null ? "" : query);
        payload.put("diseases", diseases.size() > 12 ? diseases.subList(0, 12) : diseases);
        payload.put("medicines", medicines.size() > 12 ? medicines.subList(0, 12) : medicines);
        sendJson(exchange, payload);
    }

    private static void handleFeedback(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String username = str(request.get("username")).trim();
            String type = str(request.get("type")).trim();
            String content = str(request.get("content")).trim();
            String contact = str(request.get("contact")).trim();
            if (username.isBlank() || content.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请填写反馈内容"));
                return;
            }
            if (type.isBlank()) type = "功能建议";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO feedbacks (username, type, content, contact) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, type);
                stmt.setString(3, content);
                stmt.setString(4, contact);
                stmt.executeUpdate();
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "反馈提交失败：" + e.getMessage()));
                return;
            }
            sendJson(exchange, Map.of("success", true, "message", "反馈已提交，感谢您的建议"));
            return;
        }

        String username = readQueryParam(exchange, "username");
        boolean all = username == null || username.isBlank() || "all".equals(username);
        String sql = "SELECT id, username, type, content, contact, status, reply, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created FROM feedbacks" +
                (all ? "" : " WHERE username = ?") + " ORDER BY id DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!all) stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("username", rs.getString("username"));
                    item.put("type", rs.getString("type"));
                    item.put("content", rs.getString("content"));
                    item.put("contact", rs.getString("contact"));
                    item.put("status", rs.getString("status"));
                    item.put("reply", rs.getString("reply"));
                    item.put("createdAt", rs.getString("created"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "反馈读取失败"));
            return;
        }
        sendJson(exchange, Map.of("feedbacks", list, "count", list.size()));
    }

    private static void handleAdminFeedback(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        int id = safeInt(request.get("id"), 0);
        String reply = str(request.get("reply")).trim();
        String status = str(request.get("status")).trim();
        if (status.isBlank()) status = reply.isBlank() ? "待处理" : "已处理";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE feedbacks SET reply = ?, status = ? WHERE id = ?")) {
            stmt.setString(1, reply);
            stmt.setString(2, status);
            stmt.setInt(3, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "反馈更新失败"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "message", "已更新反馈状态"));
    }

    private static void handleAdminMedicine(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String action = str(request.get("action")).trim();
        String name = str(request.get("name")).trim();
        if (name.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "药品名称不能为空"));
            return;
        }
        try (Connection conn = getConnection()) {
            if ("delete".equals(action)) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM medicines WHERE name = ?")) {
                    stmt.setString(1, name);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true, "message", "已删除药品：" + name));
                return;
            }
            String category = str(request.get("category")).trim();
            String use = str(request.get("use")).trim();
            String price = str(request.get("price")).trim();
            String brand = str(request.get("brand")).trim();
            if (category.isBlank() || use.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "分类和用途不能为空"));
                return;
            }
            if (price.isBlank()) price = "¥0.0";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO medicines (name, category, use_desc, price, brand) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE category = VALUES(category), use_desc = VALUES(use_desc), " +
                    "price = VALUES(price), brand = VALUES(brand)")) {
                stmt.setString(1, name);
                stmt.setString(2, category);
                stmt.setString(3, use);
                stmt.setString(4, price);
                stmt.setString(5, brand.isEmpty() ? null : brand);
                stmt.executeUpdate();
            }
            sendJson(exchange, Map.of("success", true, "message", "药品已保存：" + name));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "药品保存失败：" + e.getMessage()));
        }
    }

    private static void handleAdminDisease(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String action = str(request.get("action")).trim();
        String name = str(request.get("name")).trim();
        if (name.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "病种名称不能为空"));
            return;
        }
        try (Connection conn = getConnection()) {
            if ("delete".equals(action)) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM diseases WHERE name = ?")) {
                    stmt.setString(1, name);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true, "message", "已删除病种：" + name));
                return;
            }
            String category = str(request.get("category")).trim();
            String tag = str(request.get("tag")).trim();
            String causes = str(request.get("causes")).trim();
            String symptoms = str(request.get("symptoms")).trim();
            String focus = str(request.get("focus")).trim();
            String description = str(request.get("description")).trim();
            if (category.isBlank()) category = "常见疾病";
            if (tag.isBlank()) tag = "常见病";
            if (description.isBlank()) description = causes.isEmpty() ? "暂无简介" : causes;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO diseases (name, tag, category, causes, symptoms, focus, description, views) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 0) " +
                    "ON DUPLICATE KEY UPDATE tag = VALUES(tag), category = VALUES(category), " +
                    "causes = VALUES(causes), symptoms = VALUES(symptoms), focus = VALUES(focus), " +
                    "description = VALUES(description)")) {
                stmt.setString(1, name);
                stmt.setString(2, tag);
                stmt.setString(3, category);
                stmt.setString(4, causes);
                stmt.setString(5, symptoms);
                stmt.setString(6, focus);
                stmt.setString(7, description);
                stmt.executeUpdate();
            }
            sendJson(exchange, Map.of("success", true, "message", "病种已保存：" + name));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "病种保存失败：" + e.getMessage()));
        }
    }

    private static void handleProfile(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleSaveProfile(exchange);
            return;
        }
        handleGetProfile(exchange);
    }

    private static void handleGetProfile(HttpExchange exchange) throws IOException {
        String username = readQueryParam(exchange, "username");
        if (username == null || username.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        int memberId = safeInt(readQueryParam(exchange, "memberId"), 0);
        sendJson(exchange, Map.of("success", true, "profile", loadProfile(username, memberId)));
    }

    private static void handleSaveProfile(HttpExchange exchange) throws IOException {
        Map<String, Object> request = readJson(exchange);
        String username = str(request.get("username")).trim();
        if (username.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        boolean saved = saveProfile(username, request);
        if (saved) {
            sendJson(exchange, Map.of("success", true, "message", "健康档案已保存"));
        } else {
            sendJson(exchange, 500, Map.of("success", false, "message", "保存失败"));
        }
    }

    private static Map<String, Object> loadProfile(String username) {
        return loadProfile(username, 0);
    }

    /** 档案读取：医疗字段来自当前成员，联系信息仍取账号级 */
    private static Map<String, Object> loadProfile(String username, int memberId) {
        Map<String, Object> profile = new HashMap<>();
        try (Connection conn = getConnection()) {
            if (memberId <= 0) memberId = defaultMemberId(conn, username);
            profile.put("memberId", memberId);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT name, gender, birth_date, height_cm, weight_kg, blood_type, allergies, chronic, "
                            + "pregnant, egfr, alt_value FROM family_members WHERE id = ? AND owner = ?")) {
                stmt.setInt(1, memberId);
                stmt.setString(2, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String birth = rs.getString("birth_date");
                        int months = ageMonthsOf(birth);
                        profile.put("name", str(rs.getString("name")));
                        profile.put("gender", str(rs.getString("gender")));
                        profile.put("birthDate", birth == null ? "" : birth.substring(0, 10));
                        profile.put("age", months < 0 ? "" : String.valueOf(months / 12));
                        profile.put("ageMonths", months);
                        profile.put("bloodType", str(rs.getString("blood_type")));
                        profile.put("height", str(rs.getString("height_cm")));
                        profile.put("weight", str(rs.getString("weight_kg")));
                        profile.put("allergies", str(rs.getString("allergies")));
                        profile.put("chronic", str(rs.getString("chronic")));
                        profile.put("pregnant", str(rs.getString("pregnant")));
                        profile.put("egfr", str(rs.getString("egfr")));
                        profile.put("alt", str(rs.getString("alt_value")));
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT notes, phone, email FROM health_profiles WHERE username = ?")) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        profile.put("notes", str(rs.getString("notes")));
                        profile.put("phone", str(rs.getString("phone")));
                        profile.put("email", str(rs.getString("email")));
                    }
                }
            }
        } catch (SQLException ignored) {
            // 读取失败时返回空档案
        }
        return profile;
    }

    /** 档案保存：医疗字段写家庭成员表，联系信息写账号表 */
    private static boolean saveProfile(String username, Map<String, Object> data) {
        try (Connection conn = getConnection()) {
            int memberId = resolveMemberId(conn, username, str(data.get("memberId")));
            // 只带电话/邮箱/备注的请求不能把成员档案写空
            boolean medical = data.containsKey("name") || data.containsKey("birthDate")
                    || data.containsKey("gender") || data.containsKey("height") || data.containsKey("weight")
                    || data.containsKey("allergies") || data.containsKey("chronic") || data.containsKey("bloodType");
            if (!medical) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO health_profiles (username, notes, phone, email) VALUES (?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE notes = VALUES(notes), phone = VALUES(phone), email = VALUES(email)")) {
                    stmt.setString(1, username);
                    stmt.setString(2, str(data.get("notes")));
                    stmt.setString(3, str(data.get("phone")));
                    stmt.setString(4, str(data.get("email")));
                    stmt.executeUpdate();
                }
                return true;
            }
            String name = str(data.get("name")).trim();
            String gender = str(data.get("gender")).trim();
            String bloodType = str(data.get("bloodType")).trim();
            String allergies = str(data.get("allergies")).trim();
            String chronic = str(data.get("chronic")).trim();
            String height = str(data.get("height")).trim();
            String weight = str(data.get("weight")).trim();
            String pregnant = str(data.get("pregnant")).trim();
            String birthRaw = str(data.get("birthDate")).trim();

            String birth = "";
            if (!birthRaw.isEmpty()) {
                try {
                    birth = LocalDate.parse(birthRaw.substring(0, Math.min(10, birthRaw.length())).replace('/', '-'))
                            .toString();
                } catch (Exception ignored) {
                    birth = "";
                }
            } else {
                // 旧表单只填年龄：按「今天减去 N 年」折算，够用但不精确
                int age = safeInt(data.get("age"), -1);
                if (age >= 0 && age <= 130) birth = LocalDate.now().minusYears(age).toString();
            }

            if (memberId > 0) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE family_members SET name = COALESCE(NULLIF(?, ''), name), gender = ?, "
                                + "birth_date = ?, height_cm = ?, weight_kg = ?, blood_type = ?, allergies = ?, "
                                + "chronic = ?, pregnant = COALESCE(NULLIF(?, ''), pregnant) WHERE id = ? AND owner = ?")) {
                    stmt.setString(1, name);
                    stmt.setString(2, gender.isEmpty() ? null : gender);
                    stmt.setString(3, birth.isEmpty() ? null : birth);
                    setNullableDecimal(stmt, 4, numOrNull(height));
                    setNullableDecimal(stmt, 5, numOrNull(weight));
                    stmt.setString(6, bloodType.isEmpty() ? null : bloodType);
                    stmt.setString(7, allergies.isEmpty() ? null : allergies);
                    stmt.setString(8, chronic.isEmpty() ? null : chronic);
                    stmt.setString(9, pregnant);
                    stmt.setInt(10, memberId);
                    stmt.setString(11, username);
                    stmt.executeUpdate();
                }
            }

            String sql = "INSERT INTO health_profiles (username, name, gender, age, blood_type, height, weight, allergies, chronic, notes, phone, email) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), gender = VALUES(gender), age = VALUES(age), "
                    + "blood_type = VALUES(blood_type), height = VALUES(height), weight = VALUES(weight), "
                    + "allergies = VALUES(allergies), chronic = VALUES(chronic), notes = VALUES(notes), "
                    + "phone = VALUES(phone), email = VALUES(email)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int months = ageMonthsOf(birth);
                String ageText = months < 0 ? str(data.get("age")) : String.valueOf(months / 12);
                stmt.setString(1, username);
                stmt.setString(2, name);
                stmt.setString(3, gender);
                stmt.setString(4, ageText);
                stmt.setString(5, bloodType);
                stmt.setString(6, height);
                stmt.setString(7, weight);
                stmt.setString(8, allergies);
                stmt.setString(9, chronic);
                stmt.setString(10, str(data.get("notes")));
                stmt.setString(11, str(data.get("phone")));
                stmt.setString(12, str(data.get("email")));
                stmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void handleRecords(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleSaveRecord(exchange);
            return;
        }
        handleGetRecords(exchange);
    }

    private static void handleGetRecords(HttpExchange exchange) throws IOException {
        String username = readQueryParam(exchange, "username");
        if (username == null || username.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "records", loadRecords(username)));
    }

    private static void handleSaveRecord(HttpExchange exchange) throws IOException {
        Map<String, Object> request = readJson(exchange);
        String username = str(request.get("username")).trim();
        String content = str(request.get("content")).trim();
        if (username.isEmpty() || content.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名或内容"));
            return;
        }

        boolean saved = saveRecord(username, content);
        if (saved) {
            sendJson(exchange, Map.of("success", true, "message", "已记录"));
        } else {
            sendJson(exchange, 500, Map.of("success", false, "message", "记录失败"));
        }
    }

    private static List<Map<String, Object>> loadRecords(String username) {
        List<Map<String, Object>> records = new ArrayList<>();
        String sql = "SELECT content, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at " +
                "FROM chat_records WHERE username = ? ORDER BY id DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("content", str(rs.getString("content")));
                    item.put("createdAt", str(rs.getString("created_at")));
                    records.add(item);
                }
            }
        } catch (SQLException ignored) {
            // 读取失败时返回空列表
        }
        return records;
    }

    private static boolean saveRecord(String username, String content) {
        String sql = "INSERT INTO chat_records (username, content) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, content);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void handleDoctorRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String username = str(request.get("username")).trim();
        String password = str(request.get("password")).trim();
        String name = str(request.get("name")).trim();
        String specialty = str(request.get("specialty")).trim();
        if (username.isEmpty() || password.isEmpty() || name.isEmpty() || specialty.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "请填写完整信息"));
            return;
        }
        if (registerDoctor(username, password, name, specialty)) {
            sendJson(exchange, Map.of("success", true, "message", "医生注册成功"));
        } else {
            sendJson(exchange, 409, Map.of("success", false, "message", "用户名已存在"));
        }
    }

    private static boolean registerDoctor(String username, String password, String name, String specialty) {
        String sql = "INSERT INTO doctors (username, password, name, specialty) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, name);
            stmt.setString(4, specialty);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void handleDoctorLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String username = str(request.get("username")).trim();
        String password = str(request.get("password")).trim();
        Map<String, Object> doctor = loadDoctor(username, password);
        if (doctor != null) {
            sendJson(exchange, Map.of("success", true, "message", "登录成功", "doctor", doctor));
        } else {
            sendJson(exchange, 401, Map.of("success", false, "message", "账号或密码错误"));
        }
    }

    private static Map<String, Object> loadDoctor(String username, String password) {
        String sql = "SELECT username, name, specialty FROM doctors WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("username", rs.getString("username"));
                    d.put("name", rs.getString("name"));
                    d.put("specialty", rs.getString("specialty"));
                    return d;
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private static void handleConsult(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String patient = str(request.get("patient")).trim();
        String condition = str(request.get("condition")).trim();
        if (patient.isEmpty() || condition.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "请填写患者状况"));
            return;
        }

        String specialty = inferSpecialty(condition);
        Map<String, Object> doctor = findDoctorBySpecialty(specialty);
        if (doctor == null) {
            sendJson(exchange, 404, Map.of("success", false, "message", "该方向暂无可接诊医生，请稍后再试"));
            return;
        }

        if (createConsultation(patient, doctor, specialty, condition)) {
            sendJson(exchange, Map.of("success", true, "message", "提交成功，已为您分配到「" + specialty + "」的 " + doctor.get("name") + " 医生，请等待医生回复"));
        } else {
            sendJson(exchange, 500, Map.of("success", false, "message", "提交失败"));
        }
    }

    private static String inferSpecialty(String condition) {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("呼吸科", List.of("咳嗽", "咳痰", "呼吸", "胸闷", "气短", "哮喘", "支气管", "肺炎", "咽喉", "喉咙", "嗓子", "咽痛", "鼻塞", "流鼻涕", "打喷嚏", "感冒", "鼻炎"));
        keywords.put("消化科", List.of("腹痛", "肚子", "腹泻", "拉肚子", "恶心", "呕吐", "胃", "肠", "反酸", "烧心", "食欲", "消化不良", "腹胀", "便秘"));
        keywords.put("皮肤科", List.of("皮肤", "疹", "痒", "湿疹", "红肿", "痘痘", "脱屑", "荨麻疹"));
        keywords.put("儿科", List.of("儿童", "孩子", "宝宝", "小儿", "婴儿", "小孩"));
        keywords.put("神经内科", List.of("头痛", "头疼", "头晕", "眩晕", "失眠", "麻木", "抽搐", "偏头痛", "乏力", "睡眠"));
        keywords.put("外科", List.of("外伤", "扭伤", "骨折", "伤口", "肿胀", "关节", "摔伤", "割伤"));
        keywords.put("内科", List.of("发热", "发烧", "低热", "高烧", "高血压", "糖尿病", "血糖", "心慌", "心悸"));

        String best = "全科";
        int bestCount = 0;
        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            int count = 0;
            for (String kw : entry.getValue()) {
                if (condition.contains(kw)) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static Map<String, Object> findDoctorBySpecialty(String specialty) {
        Map<String, Object> doctor = findDoctorExact(specialty);
        if (doctor == null && !"全科".equals(specialty)) {
            doctor = findDoctorExact("全科");
        }
        if (doctor == null) {
            doctor = findAnyDoctor();
        }
        return doctor;
    }

    private static Map<String, Object> findDoctorExact(String specialty) {
        String sql = "SELECT d.username, d.name, d.specialty, " +
                "(SELECT COUNT(*) FROM consultations c WHERE c.doctor_username = d.username AND c.status = '待回复') AS pending " +
                "FROM doctors d WHERE d.specialty = ? ORDER BY pending ASC, d.id ASC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, specialty);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("username", rs.getString("username"));
                    d.put("name", rs.getString("name"));
                    d.put("specialty", rs.getString("specialty"));
                    return d;
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private static Map<String, Object> findAnyDoctor() {
        String sql = "SELECT username, name, specialty FROM doctors ORDER BY id ASC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("username", rs.getString("username"));
                    d.put("name", rs.getString("name"));
                    d.put("specialty", rs.getString("specialty"));
                    return d;
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private static boolean createConsultation(String patient, Map<String, Object> doctor, String specialty, String condition) {
        String sql = "INSERT INTO consultations (patient_username, doctor_username, doctor_name, specialty, cond_text, status) " +
                "VALUES (?, ?, ?, ?, ?, '待回复')";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patient);
            stmt.setString(2, str(doctor.get("username")));
            stmt.setString(3, str(doctor.get("name")));
            stmt.setString(4, specialty);
            stmt.setString(5, condition);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void handlePatientConsultations(HttpExchange exchange) throws IOException {
        String username = readQueryParam(exchange, "username");
        if (username == null || username.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        markConsultationsRead(username);
        sendJson(exchange, Map.of("success", true, "consultations", loadPatientConsultations(username)));
    }

    private static void handleDoctorConsultations(HttpExchange exchange) throws IOException {
        String username = readQueryParam(exchange, "username");
        if (username == null || username.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "consultations", loadDoctorConsultations(username)));
    }

    private static void handleDoctorReply(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        int id = safeInt(request.get("id"), -1);
        String reply = str(request.get("reply")).trim();
        if (id < 0 || reply.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少回复内容"));
            return;
        }
        if (saveReply(id, reply)) {
            sendJson(exchange, Map.of("success", true, "message", "回复已发送"));
        } else {
            sendJson(exchange, 500, Map.of("success", false, "message", "回复失败"));
        }
    }

    /** 医生端：可选患者列表（附带最近一次问诊情况，方便开方时参考） */
    private static void handleDoctorPatients(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.username, IFNULL(hp.name, '') AS name, " +
                "(SELECT COUNT(*) FROM consultations c WHERE c.patient_username = u.username) AS consults, " +
                "(SELECT COUNT(*) FROM medication_plans p WHERE p.username = u.username AND p.source = 'doctor') AS scripts, " +
                "(SELECT DATE_FORMAT(MAX(c.created_at), '%Y-%m-%d') FROM consultations c WHERE c.patient_username = u.username) AS last_visit, " +
                "(SELECT c.cond_text FROM consultations c WHERE c.patient_username = u.username ORDER BY c.id DESC LIMIT 1) AS last_condition " +
                "FROM users u LEFT JOIN health_profiles hp ON hp.username = u.username ORDER BY u.id DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("username", str(rs.getString("username")));
                item.put("name", str(rs.getString("name")));
                item.put("consults", rs.getInt("consults"));
                item.put("scripts", rs.getInt("scripts"));
                item.put("lastVisit", str(rs.getString("last_visit")));
                item.put("lastCondition", str(rs.getString("last_condition")));
                list.add(item);
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "患者列表读取失败：" + e.getMessage()));
            return;
        }
        sendJson(exchange, Map.of("success", true, "patients", list));
    }

    /** 医生端：开具 / 停用 / 删除用药方案（写入患者药箱的提醒计划） */
    private static void handleDoctorPrescriptions(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String action = str(request.get("action")).trim();
            String doctorUsername = str(request.get("doctorUsername")).trim();
            if (doctorUsername.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请先以医生身份登录"));
                return;
            }
            int id = safeInt(request.get("id"), 0);
            try (Connection conn = getConnection()) {
                if ("delete".equals(action)) {
                    if (id <= 0) {
                        sendJson(exchange, 400, Map.of("success", false, "message", "缺少要删除的方案编号，请刷新页面后重试"));
                        return;
                    }
                    // 事务删除：打卡记录 + 方案一起删干净，任一步失败整体回滚，避免「医生端没了、患者端还在」
                    boolean oldAutoCommit = conn.getAutoCommit();
                    int deletedLogs = 0;
                    int deletedPlans = 0;
                    try {
                        conn.setAutoCommit(false);
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "DELETE FROM medication_logs WHERE plan_id = ?")) {
                            stmt.setInt(1, id);
                            deletedLogs = stmt.executeUpdate();
                        }
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "DELETE FROM medication_plans WHERE id = ? AND doctor_username = ? AND source = 'doctor'")) {
                            stmt.setInt(1, id);
                            stmt.setString(2, doctorUsername);
                            deletedPlans = stmt.executeUpdate();
                        }
                        if (deletedPlans == 0) {
                            conn.rollback();
                            conn.setAutoCommit(oldAutoCommit);
                            sendJson(exchange, 404, Map.of("success", false, "deleted", 0,
                                    "message", "未找到这条方案：可能已被删除，或不是由当前医生开具，请刷新页面后重试"));
                            return;
                        }
                        conn.commit();
                        conn.setAutoCommit(oldAutoCommit);
                    } catch (SQLException e) {
                        try { conn.rollback(); conn.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
                        sendJson(exchange, 500, Map.of("success", false, "message", "方案删除失败：" + e.getMessage()));
                        return;
                    }
                    Map<String, Object> ok = new LinkedHashMap<>();
                    ok.put("success", true);
                    ok.put("deleted", deletedPlans);
                    ok.put("deletedLogs", deletedLogs);
                    ok.put("message", "方案已删除，患者端提醒同步移除");
                    sendJson(exchange, ok);
                    return;
                }
                if ("toggle".equals(action)) {
                    if (id <= 0) {
                        sendJson(exchange, 400, Map.of("success", false, "message", "缺少方案编号，请刷新页面后重试"));
                        return;
                    }
                    int changed;
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE medication_plans SET active = 1 - active WHERE id = ? AND doctor_username = ? AND source = 'doctor'")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, doctorUsername);
                        changed = stmt.executeUpdate();
                    }
                    if (changed == 0) {
                        sendJson(exchange, 404, Map.of("success", false,
                                "message", "未找到这条方案，可能已被删除，请刷新页面"));
                        return;
                    }
                    sendJson(exchange, Map.of("success", true, "message", "方案状态已更新"));
                    return;
                }

                String patient = str(request.get("patient")).trim();
                String name = str(request.get("medicineName")).trim();
                String times = normalizeTimes(str(request.get("times")));
                if (patient.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请先选择患者"));
                    return;
                }
                if (name.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请选择或填写药品名称"));
                    return;
                }
                if (times.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请至少填写一个服药时间，如 08:00"));
                    return;
                }
                String dosage = str(request.get("dosage")).trim();
                String startDate = str(request.get("startDate")).trim();
                String endDate = str(request.get("endDate")).trim();
                String note = str(request.get("note")).trim();
                String doctorName = str(request.get("doctorName")).trim();
                if (startDate.isBlank()) startDate = today();
                int memberId = resolveMemberId(conn, patient, str(request.get("memberId")));
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO medication_plans (username, member_id, medicine_name, dosage, times, start_date, end_date, active, " +
                        "doctor_username, doctor_name, note, source, patient_read) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 'doctor', 0)")) {
                    stmt.setString(1, patient);
                    stmt.setInt(2, memberId > 0 ? memberId : 0);
                    stmt.setString(3, name);
                    stmt.setString(4, dosage);
                    stmt.setString(5, times);
                    stmt.setString(6, startDate);
                    stmt.setString(7, endDate.isEmpty() ? null : endDate);
                    stmt.setString(8, doctorUsername);
                    stmt.setString(9, doctorName);
                    stmt.setString(10, note);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true,
                        "message", "已下达用药方案：" + name + " " + times.replace(",", " / ")));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "方案保存失败：" + e.getMessage()));
            }
            return;
        }

        String doctor = str(readQueryParam(exchange, "doctor")).trim();
        int filterMember = 0;
        try { filterMember = Integer.parseInt(str(readQueryParam(exchange, "memberId")).trim()); } catch (Exception e) { filterMember = 0; }
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.id, p.username, p.member_id, p.medicine_name, p.dosage, p.times, p.start_date, p.end_date, " +
                "p.active, p.note, p.doctor_name, DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i') AS created, " +
                "IFNULL(hp.name, '') AS patient_name, IFNULL(fm.name, '') AS member_name " +
                "FROM medication_plans p LEFT JOIN health_profiles hp ON hp.username = p.username " +
                "LEFT JOIN family_members fm ON fm.id = p.member_id " +
                "WHERE p.source = 'doctor'" + (doctor.isBlank() ? "" : " AND p.doctor_username = ?") +
                (filterMember > 0 ? " AND p.member_id = ?" : "") +
                " ORDER BY p.id DESC LIMIT 100";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int pi = 1;
            if (!doctor.isBlank()) stmt.setString(pi++, doctor);
            if (filterMember > 0) stmt.setInt(pi++, filterMember);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("patient", str(rs.getString("username")));
                    item.put("patientName", str(rs.getString("patient_name")));
                    item.put("memberId", rs.getInt("member_id"));
                    item.put("memberName", str(rs.getString("member_name")));
                    item.put("medicineName", str(rs.getString("medicine_name")));
                    item.put("dosage", str(rs.getString("dosage")));
                    item.put("times", List.of(str(rs.getString("times")).split(",")));
                    item.put("startDate", str(rs.getString("start_date")));
                    item.put("endDate", str(rs.getString("end_date")));
                    item.put("active", rs.getInt("active") == 1);
                    item.put("note", str(rs.getString("note")));
                    item.put("doctorName", str(rs.getString("doctor_name")));
                    item.put("createdAt", str(rs.getString("created")));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "方案读取失败：" + e.getMessage()));
            return;
        }
        sendJson(exchange, Map.of("success", true, "prescriptions", list));
    }

    private static void handleNotifications(HttpExchange exchange) throws IOException {
        String username = readQueryParam(exchange, "username");
        // POST：用户已查看通知，全部标记已读
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            if (username == null || username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
                return;
            }
            markConsultationsRead(username);
            sendJson(exchange, Map.of("success", true));
            return;
        }
        if (username == null || username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "count", 0));
            return;
        }
        // detail=1：返回未读的医生回复明细
        if ("1".equals(readQueryParam(exchange, "detail"))) {
            List<Map<String, Object>> items = new ArrayList<>(loadUnreadPrescriptions(username));
            items.addAll(loadUnreadReplies(username));
            sendJson(exchange, Map.of("success", true, "items", items));
            return;
        }
        sendJson(exchange, Map.of("success", true, "count", countUnread(username)));
    }

    private static List<Map<String, Object>> loadUnreadReplies(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, doctor_name, specialty, cond_text, reply, " +
                "DATE_FORMAT(replied_at, '%Y-%m-%d %H:%i') AS replied_at " +
                "FROM consultations WHERE patient_username = ? AND status = '已回复' AND patient_read = 0 " +
                "ORDER BY replied_at DESC, id DESC LIMIT 20";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("type", "reply");
                    item.put("id", rs.getInt("id"));
                    item.put("doctorName", str(rs.getString("doctor_name")));
                    item.put("specialty", str(rs.getString("specialty")));
                    item.put("condition", str(rs.getString("cond_text")));
                    item.put("reply", str(rs.getString("reply")));
                    item.put("repliedAt", str(rs.getString("replied_at")));
                    list.add(item);
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    /** 患者端通知：医生新开的用药方案（未读） */
    private static List<Map<String, Object>> loadUnreadPrescriptions(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, doctor_name, medicine_name, dosage, times, start_date, end_date, note, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created " +
                "FROM medication_plans WHERE username = ? AND source = 'doctor' AND patient_read = 0 " +
                "ORDER BY id DESC LIMIT 20";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("type", "plan");
                    item.put("id", rs.getInt("id"));
                    item.put("doctorName", str(rs.getString("doctor_name")));
                    item.put("medicineName", str(rs.getString("medicine_name")));
                    item.put("dosage", str(rs.getString("dosage")));
                    item.put("times", str(rs.getString("times")).replace(",", " / "));
                    item.put("startDate", str(rs.getString("start_date")));
                    item.put("endDate", str(rs.getString("end_date")));
                    item.put("note", str(rs.getString("note")));
                    item.put("createdAt", str(rs.getString("created")));
                    list.add(item);
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    private static List<Map<String, Object>> loadPatientConsultations(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, doctor_name, specialty, cond_text, status, reply, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at, " +
                "DATE_FORMAT(replied_at, '%Y-%m-%d %H:%i') AS replied_at " +
                "FROM consultations WHERE patient_username = ? ORDER BY id DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("doctorName", str(rs.getString("doctor_name")));
                    item.put("specialty", str(rs.getString("specialty")));
                    item.put("condition", str(rs.getString("cond_text")));
                    item.put("status", str(rs.getString("status")));
                    item.put("reply", str(rs.getString("reply")));
                    item.put("createdAt", str(rs.getString("created_at")));
                    item.put("repliedAt", str(rs.getString("replied_at")));
                    list.add(item);
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    private static List<Map<String, Object>> loadDoctorConsultations(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, patient_username, specialty, cond_text, status, reply, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM consultations WHERE doctor_username = ? ORDER BY id DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("patient", str(rs.getString("patient_username")));
                    item.put("specialty", str(rs.getString("specialty")));
                    item.put("condition", str(rs.getString("cond_text")));
                    item.put("status", str(rs.getString("status")));
                    item.put("reply", str(rs.getString("reply")));
                    item.put("createdAt", str(rs.getString("created_at")));
                    list.add(item);
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    private static void markConsultationsRead(String username) {
        String sql = "UPDATE consultations SET patient_read = 1 WHERE patient_username = ? AND patient_read = 0";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException ignored) {
        }
        String planSql = "UPDATE medication_plans SET patient_read = 1 WHERE username = ? AND source = 'doctor' AND patient_read = 0";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(planSql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private static boolean saveReply(int id, String reply) {
        String sql = "UPDATE consultations SET reply = ?, status = '已回复', replied_at = NOW(), patient_read = 0 WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reply);
            stmt.setInt(2, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private static int countUnread(String username) {
        String sql = "SELECT (SELECT COUNT(*) FROM consultations " +
                "WHERE patient_username = ? AND status = '已回复' AND patient_read = 0) " +
                "+ (SELECT COUNT(*) FROM medication_plans " +
                "WHERE username = ? AND source = 'doctor' AND patient_read = 0) AS c";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c");
                }
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private static void handleSymptoms(HttpExchange exchange) throws IOException {
        sendJson(exchange, SYMPTOMS);
    }

    private static void handleDiagnose(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }

        Map<String, Object> request = readJson(exchange);
        List<String> selected = readStringList(request.get("symptoms"));
        int duration = safeInt(request.get("duration"), 3);

        if (selected == null || selected.isEmpty()) {
            sendJson(exchange, Map.of(
                    "success", false,
                    "message", "请选择至少一个症状",
                    "disease", "感冒或轻度炎症",
                    "recommendations", List.of("对乙酰氨基酚", "休息与补水", "如持续加重请及时就医")
            ));
            return;
        }

        Map<String, List<String>> score = new HashMap<>();
        score.put("上呼吸道感染", List.of("发热", "咳嗽", "咽喉痛", "鼻塞", "流鼻涕", "乏力"));
        score.put("过敏性鼻炎", List.of("打喷嚏", "流鼻涕", "鼻塞"));
        score.put("急性肠胃炎", List.of("腹痛", "腹泻", "恶心", "乏力"));
        score.put("支气管炎", List.of("咳嗽", "胸闷", "发热"));
        score.put("胃炎", List.of("腹痛", "恶心", "反酸"));

        String disease = "感冒或轻度炎症";
        int bestCount = 0;
        for (Map.Entry<String, List<String>> entry : score.entrySet()) {
            int count = 0;
            for (String item : entry.getValue()) {
                if (selected.contains(item)) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                disease = entry.getKey();
            }
        }

        Map<String, List<String>> recommendations = new HashMap<>();
        recommendations.put("上呼吸道感染", List.of("对乙酰氨基酚缓释片", "连花清瘟胶囊", "多喝温水并休息"));
        recommendations.put("过敏性鼻炎", List.of("孟鲁司特钠片", "生理盐水鼻用喷雾", "减少接触过敏源"));
        recommendations.put("急性肠胃炎", List.of("补液盐", "思密达", "清淡饮食"));
        recommendations.put("支气管炎", List.of("止咳化痰药", "阿莫西林分散片", "注意保暖"));
        recommendations.put("胃炎", List.of("胃药制剂", "少刺激饮食", "规律作息"));
        recommendations.put("感冒或轻度炎症", List.of("对乙酰氨基酚", "休息与补水", "如持续加重请及时就医"));

        sendJson(exchange, Map.of(
                "success", true,
                "disease", disease,
                "duration", duration,
                "recommendations", recommendations.getOrDefault(disease, recommendations.get("感冒或轻度炎症")),
                "message", "根据您的症状，系统已做出参考判断。"
        ));
    }

    /* ==================== AI 问诊 ==================== */

    /** 急症关键词：命中后直接给「立即就医」结论，不依赖大模型，避免关键情况被漏判 */
    private static final String[][] EMERGENCY_TERMS = {
            {"胸痛", "胸部压榨样疼痛可能与心脏急症有关"},
            {"心口疼", "胸口疼痛需要警惕心脏问题"},
            {"呼吸困难", "呼吸困难是需要立即评估的表现"},
            {"喘不上气", "呼吸困难是需要立即评估的表现"},
            {"窒息", "气道受阻需要立即处理"},
            {"昏迷", "意识障碍需要立即送医"},
            {"意识不清", "意识障碍需要立即送医"},
            {"晕倒", "晕厥需要排查心脑血管原因"},
            {"抽搐", "抽搐发作需要急诊处理"},
            {"大出血", "大量出血需要立即止血并送医"},
            {"呕血", "呕血提示消化道出血"},
            {"咯血", "咯血需要尽快查明原因"},
            {"黑便", "黑便提示消化道出血可能"},
            {"剧烈头痛", "突发剧烈头痛需要排除脑血管意外"},
            {"说话不清", "言语不清、口角歪斜需要警惕脑卒中"},
            {"一侧无力", "单侧肢体无力需要警惕脑卒中"},
            {"药物过量", "药物过量需要立即急诊处理"},
            {"自杀", "出现自伤念头请立即联系身边的人并就医"}
    };

    /** 需要升级为「近期门诊」的提示性症状 */
    private static final String[] WATCH_TERMS = {
            "高热", "持续发热", "血便", "剧烈腹痛", "反复呕吐", "心悸", "体重下降", "浮肿", "黄疸", "呼吸急促"
    };

    private static void handleAIConsult(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 POST 请求"));
            return;
        }

        Map<String, Object> request = readJson(exchange);
        List<String> basicQuestions = readStringList(request.get("basicQuestions"));
        List<String> symptoms = readStringList(request.get("symptoms"));
        String question = str(request.get("question")).trim();
        String username = str(request.get("username")).trim();
        List<Map<String, String>> history = readChatHistory(request.get("history"));

        if (basicQuestions.isEmpty() && symptoms.isEmpty() && question.isEmpty()) {
            sendJson(exchange, Map.of(
                    "success", false,
                    "answer", "请至少选择一个基础问题，或描述您的主要健康情况。"));
            return;
        }

        sendJson(exchange, generateAIHealthAnswer(basicQuestions, symptoms, question, history, username));
    }

    /** 读取前端传来的多轮对话（只取最近 8 条，避免提示词过长） */
    private static List<Map<String, String>> readChatHistory(Object value) {
        List<Map<String, String>> list = new ArrayList<>();
        if (!(value instanceof List<?> items)) {
            return list;
        }
        for (Object item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (item instanceof Map<?, ?> raw) {
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else if (item != null) {
                map = parseJsonObject(String.valueOf(item));
            }
            String role = str(map.get("role")).trim();
            String text = str(map.get("text")).trim();
            if (text.isEmpty()) continue;
            if ("bot".equals(role) && text.startsWith("正在思考")) continue;
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", role.isEmpty() ? "user" : role);
            msg.put("text", text);
            list.add(msg);
        }
        int from = Math.max(0, list.size() - 8);
        return new ArrayList<>(list.subList(from, list.size()));
    }

    private static Map<String, Object> generateAIHealthAnswer(List<String> basicQuestions, List<String> symptoms,
                                                              String question, List<Map<String, String>> history,
                                                              String username) {
        StringBuilder context = new StringBuilder();
        if (!basicQuestions.isEmpty()) {
            context.append("基础问题：").append(String.join("、", basicQuestions)).append("。");
        }
        if (!symptoms.isEmpty()) {
            context.append("症状：").append(String.join("、", symptoms)).append("。");
        }
        if (!question.isEmpty()) {
            context.append("用户描述：").append(question);
        }
        String fullText = context.toString();

        // 1. 急症规则优先：先保命，再交给模型补充
        for (String[] term : EMERGENCY_TERMS) {
            if (fullText.contains(term[0])) {
                return emergencyResult(term[0], term[1]);
            }
        }

        // 2. 大模型结构化问诊
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String raw = callDeepSeek(buildConsultPrompt(fullText, history), apiKey);
                Map<String, Object> parsed = parseLooseJson(raw);
                if (parsed != null && !str(parsed.get("summary")).isBlank()) {
                    return structureResult(parsed, fullText, username);
                }
                if (raw != null && !raw.isBlank()) {
                    // 模型没按 JSON 返回：整段当作结论，其余字段留空
                    List<String> empty = new ArrayList<>();
                    List<String> advice = new ArrayList<>();
                    String[] lines = raw.replace("\r", "").split("\n");
                    for (String line : lines) {
                        String clean = line.replaceAll("^[#>*\\-\\s]+", "").trim();
                        if (!clean.isEmpty()) advice.add(clean);
                    }
                    String summary = lines.length > 0 ? lines[0].replaceAll("^[#>*\\-\\s]+", "").trim() : raw.trim();
                    return consultResult("amber", summary, empty, advice, empty, empty, empty,
                            "AI 大模型（DeepSeek）", "", fullText.contains("发热") || fullText.contains("咳嗽"));
                }
            } catch (Exception ignored) {
                // 网络或解析失败时走本地规则
            }
        }

        // 3. 无密钥 / 调用失败：本地知识库兜底
        return ruleBasedResult(fullText);
    }

    private static String buildConsultPrompt(String context, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「智能在线医生」，为普通用户做初步健康咨询。\n");
        sb.append("要求：不做确诊、不开处方、语气温和；建议具体可执行；不要输出 markdown，不要在 JSON 外用任何文字。\n");
        sb.append("只输出一个 JSON 对象，字段全部为字符串，多个条目用竖线 | 分隔，每条不超过 24 个字：\n");
        sb.append("{\"triage\":\"green|amber|red\",\"summary\":\"一句话判断，40 字内\",");
        sb.append("\"possible\":\"可能原因1|可能原因2\",\"advice\":\"建议1|建议2|建议3\",");
        sb.append("\"redFlags\":\"需要就医的信号1|信号2\",\"followups\":\"还需要了解的追问1|追问2\",");
        sb.append("\"medicines\":\"常用药1::一句用法提醒|常用药2::提醒\"}\n");
        sb.append("triage 含义：green=可居家观察，amber=建议 1-2 天内门诊，red=建议立即就医。\n");
        if (!history.isEmpty()) {
            sb.append("对话历史：\n");
            for (Map<String, String> msg : history) {
                sb.append("user".equals(msg.get("role")) ? "用户：" : "医生：").append(msg.get("text")).append("\n");
            }
        }
        sb.append("本次用户情况：").append(context);
        return sb.toString();
    }

    private static Map<String, Object> structureResult(Map<String, Object> parsed, String fullText, String username) {
        String triage = normalizeTriage(str(parsed.get("triage")));
        if ("green".equals(triage)) {
            for (String term : WATCH_TERMS) {
                if (fullText.contains(term)) {
                    triage = "amber";
                    break;
                }
            }
        }
        List<String> possible = toList(parsed.get("possible"));
        List<String> advice = toList(parsed.get("advice"));
        List<String> redFlags = toList(parsed.get("redFlags"));
        List<String> followups = toList(parsed.get("followups"));
        List<String> medicines = toList(parsed.get("medicines"));
        String cabinetHint = cabinetOverlap(username, advice, medicines);
        return consultResult(triage, str(parsed.get("summary")).trim(), possible, advice, redFlags,
                followups, medicines, "AI 大模型（DeepSeek）", cabinetHint,
                !"green".equals(triage));
    }

    private static Map<String, Object> emergencyResult(String term, String reason) {
        List<String> advice = new ArrayList<>(List.of(
                "立即停止活动，坐下或半卧休息，保持环境通风",
                "尽快前往最近医院急诊；症状明显或无法自行前往时拨打 120",
                "带上正在服用的药物清单和既往病历，方便医生快速判断"));
        List<String> redFlags = new ArrayList<>(List.of(term, "面色苍白、大汗、四肢发冷", "症状持续不缓解或反复发作"));
        return consultResult("red", "你提到「" + term + "」，" + reason + "，属于需要立即处理的信号。",
                new ArrayList<>(), advice, redFlags, new ArrayList<>(), new ArrayList<>(),
                "急症识别规则", "", true);
    }

    private static Map<String, Object> ruleBasedResult(String text) {
        String summary;
        String triage;
        List<String> possible = new ArrayList<>();
        List<String> advice = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();
        List<String> followups = new ArrayList<>();
        if (text.contains("发热") || text.contains("咳嗽") || text.contains("鼻塞") || text.contains("咽喉痛")) {
            triage = "amber";
            summary = "发热、咳嗽、鼻塞、咽喉痛常见于上呼吸道感染或轻度炎症。";
            possible.addAll(List.of("上呼吸道感染", "急性咽炎", "过敏性鼻炎"));
            advice.addAll(List.of("多喝温水，保证睡眠，避免熬夜", "饮食清淡，室内保持通风", "体温超过 38.5℃ 可按说明书用退热药"));
            redFlags.addAll(List.of("发热超过 3 天不退", "出现呼吸困难或胸痛", "精神明显变差、无法进食"));
            followups.addAll(List.of("症状持续几天了？", "最高体温多少？"));
        } else if (text.contains("腹泻") || text.contains("腹痛") || text.contains("恶心")) {
            triage = "amber";
            summary = "腹泻、腹痛、恶心常见于急性胃肠炎或饮食不适。";
            possible.addAll(List.of("急性胃肠炎", "饮食不洁", "肠易激综合征"));
            advice.addAll(List.of("少量多次补充淡盐水或口服补液盐", "少油少辣，先吃粥、面条等易消化食物", "注意手卫生，餐具分开"));
            redFlags.addAll(List.of("大便带血或呈黑色", "持续呕吐无法进水", "明显口干、尿少等脱水表现"));
            followups.addAll(List.of("一天腹泻几次？", "有没有发热或呕吐？"));
        } else if (text.contains("头痛") || text.contains("头晕") || text.contains("失眠")) {
            triage = "green";
            summary = "头痛、头晕、睡眠差多与疲劳、紧张或作息紊乱有关。";
            possible.addAll(List.of("紧张性头痛", "睡眠不足", "颈椎劳损"));
            advice.addAll(List.of("规律作息，固定入睡与起床时间", "减少连续用屏，每小时活动颈肩", "少咖啡因和酒精"));
            redFlags.addAll(List.of("突发剧烈头痛", "伴喷射性呕吐或视物模糊", "一侧肢体无力"));
            followups.addAll(List.of("头痛是胀痛还是跳痛？", "最近睡眠几小时？"));
        } else {
            triage = "green";
            summary = "根据你的描述，暂时没有发现需要立即处理的危险信号。";
            possible.addAll(List.of("功能性不适", "生活方式相关因素"));
            advice.addAll(List.of("记录症状出现的时间、诱因和变化", "规律作息、清淡饮食、适量饮水", "症状加重或出现新症状时及时就诊"));
            redFlags.addAll(List.of("症状持续加重", "出现高热、胸痛、呼吸困难", "影响正常进食和睡眠"));
            followups.addAll(List.of("症状持续多久了？", "有没有其他伴随症状？"));
        }
        return consultResult(triage, summary, possible, advice, redFlags, followups,
                new ArrayList<>(), "内置健康知识库", "", !"green".equals(triage));
    }

    private static Map<String, Object> consultResult(String triage, String summary, List<String> possible,
                                                     List<String> advice, List<String> redFlags,
                                                     List<String> followups, List<String> medicines,
                                                     String source, String cabinetHint, boolean suggestDoctor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("source", source);
        result.put("triage", triage);
        result.put("triageLabel", triageLabel(triage));
        result.put("summary", summary);
        result.put("possible", possible);
        result.put("advice", advice);
        result.put("redFlags", redFlags);
        result.put("followups", followups);
        result.put("medicines", medicines);
        result.put("cabinetHint", cabinetHint == null ? "" : cabinetHint);
        result.put("suggestDoctor", suggestDoctor);
        result.put("answer", composeAnswer(summary, possible, advice, redFlags, medicines));
        return result;
    }

    private static String composeAnswer(String summary, List<String> possible, List<String> advice,
                                        List<String> redFlags, List<String> medicines) {
        StringBuilder sb = new StringBuilder();
        if (!summary.isBlank()) sb.append(summary).append("\n");
        if (!possible.isEmpty()) sb.append("【可能原因】").append(String.join("、", possible)).append("\n");
        if (!advice.isEmpty()) {
            sb.append("【建议】\n");
            for (int i = 0; i < advice.size(); i++) {
                sb.append(i + 1).append(". ").append(advice.get(i)).append("\n");
            }
        }
        if (!redFlags.isEmpty()) {
            sb.append("【出现以下情况请尽快就医】").append(String.join("、", redFlags)).append("\n");
        }
        if (!medicines.isEmpty()) {
            sb.append("【可参考】").append(String.join("；", medicines)).append("\n");
        }
        sb.append("以上为健康参考，不能替代医生面诊；如症状变化请及时就医。");
        return sb.toString().trim();
    }

    private static String triageLabel(String triage) {
        if ("red".equals(triage)) return "建议立即就医";
        if ("amber".equals(triage)) return "建议近期门诊";
        return "可先居家观察";
    }

    private static String normalizeTriage(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("red") || value.contains("立即") || value.contains("急诊")) return "red";
        if (value.contains("green") || value.contains("观察")) return "green";
        return "amber";
    }

    /** 模型可能返回数组也可能返回竖线分隔字符串，统一成列表 */
    private static List<String> toList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                String text = str(item).trim();
                if (!text.isEmpty()) list.add(text);
                if (list.size() >= 6) break;
            }
            return list;
        }
        return splitPipe(str(value));
    }

    /** 把模型用竖线分隔的字符串拆成列表 */
    private static List<String> splitPipe(String value) {
        List<String> list = new ArrayList<>();
        if (value == null || value.isBlank()) return list;
        for (String part : value.split("[|｜]")) {
            String item = part.trim();
            if (!item.isEmpty()) list.add(item);
            if (list.size() >= 6) break;
        }
        return list;
    }

    /** 建议里出现药箱已有的药时给出重复用药提醒 */
    private static String cabinetOverlap(String username, List<String> advice, List<String> medicines) {
        if (username == null || username.isBlank()) return "";
        String combined = String.join(" ", advice) + " " + String.join(" ", medicines);
        StringBuilder hits = new StringBuilder();
        String sql = "SELECT medicine_name FROM cabinet_items WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = str(rs.getString("medicine_name")).trim();
                    if (name.length() < 2) continue;
                    String core = coreName(name);
                    boolean hit = combined.contains(name) || (core.length() >= 3 && combined.contains(core));
                    if (!hit) continue;
                    if (hits.length() > 0) hits.append("、");
                    hits.append(name);
                }
            }
        } catch (SQLException ignored) {
            return "";
        }
        if (hits.length() == 0) return "";
        return "你的药箱里已有「" + hits + "」，用药前请确认是否重复，必要时咨询药师。";
    }

    /* ==================== 用药安全中心 ==================== */

    /** 药名关键词 → 实际成分，用于识别「感冒药 + 退烧药」这类隐性重复 */
    private static final String[][] INGREDIENT_ALIAS = {
            {"感冒灵", "对乙酰氨基酚", "氯苯那敏"},
            {"氨酚黄那敏", "对乙酰氨基酚", "氯苯那敏"},
            {"氨酚烷胺", "对乙酰氨基酚"},
            {"维C银翘", "对乙酰氨基酚"},
            {"泰诺", "对乙酰氨基酚"},
            {"扑热息痛", "对乙酰氨基酚"},
            {"对乙酰氨基酚", "对乙酰氨基酚"},
            {"布洛芬", "布洛芬"},
            {"芬必得", "布洛芬"},
            {"阿司匹林", "阿司匹林"},
            {"双氯芬酸", "双氯芬酸"},
            {"洛索洛芬", "洛索洛芬"},
            {"氯雷他定", "氯雷他定"},
            {"西替利嗪", "西替利嗪"},
            {"氯苯那敏", "氯苯那敏"},
            {"扑尔敏", "氯苯那敏"},
            {"奥美拉唑", "奥美拉唑"},
            {"雷贝拉唑", "雷贝拉唑"},
            {"阿莫西林", "阿莫西林"},
            {"头孢", "头孢类"},
            {"左氧氟沙星", "左氧氟沙星"},
            {"二甲双胍", "二甲双胍"},
            {"格华止", "二甲双胍"},
            {"阿卡波糖", "阿卡波糖"},
            {"氨氯地平", "氨氯地平"},
            {"硝苯地平", "硝苯地平"},
            {"缬沙坦", "缬沙坦"},
            {"阿托伐他汀", "阿托伐他汀"},
            {"蒙脱石", "蒙脱石"},
            {"藿香正气", "藿香正气"}
    };

    /** 成分 → 药理类别，用于识别「同类药叠加」 */
    private static final String[][] DRUG_CLASS = {
            {"对乙酰氨基酚", "解热镇痛药"}, {"布洛芬", "解热镇痛药"}, {"阿司匹林", "解热镇痛药"},
            {"双氯芬酸", "解热镇痛药"}, {"洛索洛芬", "解热镇痛药"},
            {"氯雷他定", "抗组胺药"}, {"西替利嗪", "抗组胺药"}, {"氯苯那敏", "抗组胺药"},
            {"奥美拉唑", "质子泵抑制剂"}, {"雷贝拉唑", "质子泵抑制剂"},
            {"阿莫西林", "抗菌药"}, {"头孢类", "抗菌药"}, {"左氧氟沙星", "抗菌药"}
    };

    /** 过敏原 → 同类药物关键词，识别「青霉素过敏但药箱有阿莫西林」 */
    private static final String[][] ALLERGY_ALIAS = {
            {"青霉素", "阿莫西林", "氨苄西林", "哌拉西林", "青霉素"},
            {"头孢", "头孢"},
            {"磺胺", "磺胺", "复方新诺明"},
            {"阿司匹林", "阿司匹林"},
            {"布洛芬", "布洛芬", "芬必得"},
            {"阿莫西林", "阿莫西林"}
    };

    /** AI 复核结果缓存：key = 用药+档案签名，value = {时间戳, issues} */
    private static final Map<String, Object[]> SAFETY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long SAFETY_CACHE_MS = 10 * 60 * 1000L;

    private static void handleSafety(HttpExchange exchange) throws IOException {
        String username = str(readQueryParam(exchange, "username")).trim();
        boolean refresh = "1".equals(str(readQueryParam(exchange, "refresh")).trim());
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "empty", true, "score", 100, "level", "none",
                    "levelLabel", "待评估", "issues", List.of(),
                    "stats", Map.of("drugs", 0, "red", 0, "amber", 0, "ok", 0)));
            return;
        }
        try {
            int memberId;
            try (Connection conn = getConnection()) {
                memberId = resolveMemberId(conn, username, readQueryParam(exchange, "memberId"));
            }
            sendJson(exchange, buildSafetyReport(username, refresh, false, memberId));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "用药安全检查失败：" + e.getMessage()));
        }
    }

    /* ---------------- 医生端：开方用药安全预检 ---------------- */

    private static void handleDoctorSafety(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String patient = str(request.get("patient")).trim();
            String medicine = str(request.get("medicineName")).trim();
            if (patient.isBlank() || medicine.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "缺少患者或药品名称"));
                return;
            }
            try (Connection conn = getConnection()) {
                int memberId = resolveMemberId(conn, patient, str(request.get("memberId")));
                sendJson(exchange, previewPrescription(patient, medicine, memberId));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "用药校验失败：" + e.getMessage()));
            }
            return;
        }
        String patient = str(readQueryParam(exchange, "patient")).trim();
        if (patient.isBlank()) {
            sendJson(exchange, Map.of("success", true, "empty", true, "level", "none",
                    "levelLabel", "待评估", "issues", List.of(),
                    "stats", Map.of("drugs", 0, "red", 0, "amber", 0)));
            return;
        }
        try {
            int memberId;
            try (Connection conn = getConnection()) {
                memberId = resolveMemberId(conn, patient, readQueryParam(exchange, "memberId"));
            }
            // 医生端只要本地规则结论，AI 复核太慢，会让选中患者时明显卡顿
            sendJson(exchange, buildSafetyReport(patient, false, true, memberId));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "用药检查失败：" + e.getMessage()));
        }
    }

    /* ---------------- 医生端：拉取某患者的家庭成员列表 ---------------- */

    private static void handleDoctorPatientMembers(HttpExchange exchange) throws IOException {
        String patient = str(readQueryParam(exchange, "patient")).trim();
        if (patient.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少患者账号"));
            return;
        }
        List<Map<String, Object>> members = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT m.* FROM family_members m WHERE m.owner = ? ORDER BY m.is_default DESC, m.id")) {
            stmt.setString(1, patient);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = memberMap(rs);
                    members.add(m);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "成员读取失败：" + e.getMessage()));
            return;
        }
        sendJson(exchange, Map.of("success", true, "patient", patient, "members", members));
    }

    /* ---------------- 患者端：添加药品前的单药三层校验 ---------------- */

    private static void handleSafetyPreview(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "仅支持 POST"));
            return;
        }
        Map<String, Object> request = readJson(exchange);
        String username = str(request.get("username")).trim();
        String medicine = str(request.get("medicine")).trim();
        if (username.isBlank() || medicine.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户或药品名称"));
            return;
        }
        int memberId;
        try (Connection conn = getConnection()) {
            memberId = resolveMemberId(conn, username, str(request.get("memberId")));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "校验失败：" + e.getMessage()));
            return;
        }
        try {
            Map<String, Object> result = previewPrescription(username, medicine, memberId);
            sendJson(exchange, result);
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "校验失败：" + e.getMessage()));
        }
    }

    /* ---------------- 按体重推荐剂量 + 超量上限 ---------------- */

    private static void handleDoseSuggest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "仅支持 GET"));
            return;
        }
        String medicine = str(readQueryParam(exchange, "medicine")).trim();
        double weightKg;
        try {
            weightKg = Double.parseDouble(str(readQueryParam(exchange, "weightKg")).trim());
        } catch (Exception e) {
            weightKg = 0;
        }
        try {
            sendJson(exchange, doseSuggest(medicine, weightKg));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "剂量查询失败：" + e.getMessage()));
        }
    }

    private static Map<String, Object> doseSuggest(String medicine, double weightKg) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("found", false);
        out.put("medicine", medicine);
        out.put("weightKg", round1(weightKg));
        if (medicine.isEmpty() || weightKg <= 0) return out;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM medicine_dosing WHERE ? LIKE CONCAT('%', medicine_name, '%') "
                             + "OR ? LIKE CONCAT('%', aliases, '%') "
                             + "OR medicine_name LIKE CONCAT('%', ?, '%') "
                             + "OR aliases LIKE CONCAT('%', ?, '%') LIMIT 1")) {
            stmt.setString(1, medicine);
            stmt.setString(2, medicine);
            stmt.setString(3, medicine);
            stmt.setString(4, medicine);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return out;
                double dmin = rs.getDouble("mg_per_kg_dose_min");
                double dmax = rs.getDouble("mg_per_kg_dose_max");
                double day = rs.getDouble("mg_per_kg_day_max");
                int freq = rs.getInt("max_freq_per_day");
                String strength = str(rs.getString("strength"));
                String volUnit = str(rs.getString("vol_unit"));
                double mgPerUnit = parseStrength(strength);

                double doseMinMg = dmin * weightKg;
                double doseMaxMg = dmax * weightKg;
                double dayMaxMg = day * weightKg;

                out.put("found", true);
                out.put("matchedName", rs.getString("medicine_name"));
                out.put("aliases", rs.getString("aliases"));
                out.put("form", rs.getString("form"));
                out.put("strength", strength);
                out.put("volUnit", volUnit);
                out.put("mgPerKgDoseMin", round1(dmin));
                out.put("mgPerKgDoseMax", round1(dmax));
                out.put("mgPerKgDayMax", round1(day));
                out.put("maxFreq", freq);
                out.put("doseMinMg", round1(doseMinMg));
                out.put("doseMaxMg", round1(doseMaxMg));
                out.put("dayMaxMg", round1(dayMaxMg));
                out.put("mgPerUnit", round2(mgPerUnit));
                out.put("note", rs.getString("note"));
                if (mgPerUnit > 0 && !volUnit.isBlank()) {
                    out.put("volMin", round1(doseMinMg / mgPerUnit));
                    out.put("volMax", round1(doseMaxMg / mgPerUnit));
                    out.put("dayMaxVol", round1(dayMaxMg / mgPerUnit));
                }
            }
        }
        return out;
    }

    /** 解析浓度串 "100mg/5mL" / "0.125g/袋"，返回每最小用量单位含多少 mg；无法解析返回 0 */
    private static double parseStrength(String strength) {
        if (strength == null || strength.isBlank()) return 0;
        String[] parts = strength.split("/", 2);
        if (parts.length < 2) return 0;
        double leftMg = 0;
        java.util.regex.Matcher lm = java.util.regex.Pattern.compile("([0-9.]+)\\s*(mg|g)").matcher(parts[0]);
        if (lm.find()) {
            double v = Double.parseDouble(lm.group(1));
            leftMg = "g".equals(lm.group(2)) ? v * 1000 : v;
        }
        double right = 0;
        java.util.regex.Matcher rm = java.util.regex.Pattern.compile("([0-9.]+)\\s*(mL|ml|袋|片|粒)").matcher(parts[1]);
        if (rm.find()) {
            right = Double.parseDouble(rm.group(1));
        } else if (java.util.regex.Pattern.compile("(mL|ml|袋|片|粒)").matcher(parts[1]).find()) {
            right = 1; // 形如 "0.125g/袋"：右边只写单位，默认 1 个单位
        }
        if (right <= 0 || leftMg <= 0) return 0;
        return leftMg / right;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** 只校验「这一味新药」与患者现有情况的冲突，供开方时即时提示 */
    private static Map<String, Object> previewPrescription(String patient, String medicine, int memberId) throws SQLException {
        PatientCtx ctx = loadPatientContext(patient, memberId);
        List<String> chronicList = splitTerms(ctx.chronic);
        List<String> allergyList = splitTerms(ctx.allergies);
        List<String> newIngredients = ingredientsOf(medicine);
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> reported = new ArrayList<>();

        // 1. 与现有用药比对：同成分（红）优先于同类（橙）
        for (String drug : ctx.drugs) {
            if (drug.equals(medicine)) {
                issues.add(safetyIssue("red", "患者药箱里已有这个药", List.of(medicine),
                        "该药已在患者的在用药清单中，重复开具会产生两条提醒，容易重复服药。",
                        "如需调整剂量或时间，请先在下方「我开出的方案」里停用旧方案。"));
                reported.add(drug);
                continue;
            }
            List<String> exist = ingredientsOf(drug);
            List<String> shared = new ArrayList<>();
            for (String ing : newIngredients) {
                if (exist.contains(ing)) shared.add(ing);
            }
            if (shared.isEmpty()) continue;
            issues.add(safetyIssue("red", "与在用药成分重复", List.of(drug, medicine),
                    drug + " 与 " + medicine + " 都含「" + String.join("、", shared) + "」，同时服用会叠加剂量。",
                    "两者只保留一种；若要替换，请先停用患者原有方案。"));
            reported.add(drug);
        }

        for (String drug : ctx.drugs) {
            if (reported.contains(drug)) continue;
            String matchedClass = null;
            for (String ing : newIngredients) {
                String cls = drugClassOf(ing);
                if (cls == null) continue;
                for (String existIng : ingredientsOf(drug)) {
                    if (cls.equals(drugClassOf(existIng))) {
                        matchedClass = cls;
                        break;
                    }
                }
                if (matchedClass != null) break;
            }
            if (matchedClass == null) continue;
            issues.add(safetyIssue("amber", "与在用药同类（" + matchedClass + "）", List.of(drug, medicine),
                    drug + " 与 " + medicine + " 同属" + matchedClass + "，作用相似，叠加不增加疗效但副作用会累加。",
                    "确认是否需要替换原有药物，一般不建议同时使用。"));
        }

        // 2. 过敏史
        for (String allergy : allergyList) {
            List<String> keywords = new ArrayList<>();
            keywords.add(allergy);
            for (String[] row : ALLERGY_ALIAS) {
                if (row[0].equals(allergy)) {
                    for (int i = 1; i < row.length; i++) keywords.add(row[i]);
                }
            }
            boolean hit = false;
            for (String keyword : keywords) {
                if (keyword.length() >= 2 && medicine.contains(keyword)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            issues.add(safetyIssue("red", "患者对「" + allergy + "」过敏", List.of(medicine),
                    "健康档案记录患者对「" + allergy + "」过敏，" + medicine + " 属于同类或含该成分。",
                    "更换其他类别药物；确需使用请先评估过敏风险。"));
        }

        // 3. 慢病禁忌 + 特殊人群
        boolean elderly = ctx.age >= 65;
        boolean child = ctx.age > 0 && ctx.age < 14;
        for (String[] rule : ctx.rules) {
            if (!medicine.contains(rule[0])) continue;
            String target = rule[2];
            boolean matched;
            if ("contraindication".equals(rule[1])) {
                matched = chronicList.stream().anyMatch(c -> c.contains(target) || target.contains(c));
            } else {
                matched = ("老年人".equals(target) && elderly)
                        || ("儿童".equals(target) && child)
                        || ("孕妇".equals(target) && ctx.chronic.contains("孕"));
            }
            if (!matched) continue;
            issues.add(safetyIssue(rule[3], target + "用药提示：" + rule[0], List.of(medicine), rule[4], rule[5]));
        }

        int red = 0;
        int amber = 0;
        for (Map<String, Object> issue : issues) {
            if ("red".equals(str(issue.get("level")))) red++;
            else amber++;
        }
        String level = red > 0 ? "red" : (amber > 0 ? "amber" : "green");
        String label = red > 0 ? "不建议直接开具" : (amber > 0 ? "需确认" : "未发现冲突");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("level", level);
        result.put("levelLabel", label);
        result.put("blocked", red > 0);
        result.put("issues", issues);
        result.put("patient", patient);
        result.put("memberId", memberId);
        result.put("medicine", medicine);
        result.put("context", Map.of("drugs", ctx.drugs.size(), "age", ctx.age,
                "allergies", allergyList, "chronic", chronicList));
        result.put("summary", issues.isEmpty()
                ? "已比对患者 " + ctx.drugs.size() + " 种在用药、过敏史与慢病情况，未发现冲突"
                : "发现 " + red + " 项高风险、" + amber + " 项需确认");
        return result;
    }

    /* ---------------- 化验单拍照解读 ---------------- */

    private static String aiKey() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("OPENAI_API_KEY");
        return (apiKey == null || apiKey.isBlank()) ? null : apiKey;
    }

    private static void handleLabReport(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            String username = str(readQueryParam(exchange, "username")).trim();
            if (username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
                return;
            }
            String idText = str(readQueryParam(exchange, "id")).trim();
            try {
                if (idText.isBlank()) {
                    int memberId;
                    try (Connection conn = getConnection()) {
                        memberId = resolveMemberId(conn, username, readQueryParam(exchange, "memberId"));
                    }
                    sendJson(exchange, listLabReports(username, memberId));
                } else {
                    sendJson(exchange, loadLabReport(username, Integer.parseInt(idText)));
                }
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("success", false, "message", "报告编号不合法"));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "读取化验单失败：" + e.getMessage()));
            }
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, Map.of("success", false, "message", "只支持 GET / POST 请求"));
            return;
        }

        Map<String, Object> request = readJson(exchange);
        String action = str(request.get("action")).trim();
        String username = str(request.get("username")).trim();

        int memberId = 0;
        if (!username.isBlank()) {
            try (Connection conn = getConnection()) {
                memberId = resolveMemberId(conn, username, str(request.get("memberId")));
            } catch (SQLException ignored) {
                // 成员解析失败时退回 0，后续按账号兜底
            }
        }

        if ("delete".equals(action)) {
            int id = 0;
            try {
                id = Integer.parseInt(str(request.get("id")).trim());
            } catch (NumberFormatException ignored) {
            }
            if (username.isBlank() || id <= 0) {
                sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名或报告编号"));
                return;
            }
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM lab_reports WHERE id = ? AND username = ?")) {
                stmt.setInt(1, id);
                stmt.setString(2, username);
                int deleted = stmt.executeUpdate();
                if (deleted == 0) {
                    sendJson(exchange, 404, Map.of("success", false, "message", "未找到这份报告，可能已被删除"));
                    return;
                }
                sendJson(exchange, Map.of("success", true, "deleted", deleted, "message", "报告已删除"));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "删除失败：" + e.getMessage()));
            }
            return;
        }

        String text = str(request.get("text")).trim();
        if (username.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "缺少用户名"));
            return;
        }
        if (text.length() < 8) {
            sendJson(exchange, 400, Map.of("success", false, "message", "报告单文字太少，请重新拍照，或把检验项目粘贴到输入框里"));
            return;
        }
        if (text.length() > 12000) {
            sendJson(exchange, 400, Map.of("success", false, "message", "内容太长（超过 1.2 万字），请只保留检验项目那一段"));
            return;
        }
        String apiKey = aiKey();
        if (apiKey == null) {
            sendJson(exchange, 503, Map.of("success", false, "message", "服务端未配置大模型密钥，暂时无法解读，可以先用「智能医生」手动咨询"));
            return;
        }

        List<Map<String, Object>> items;
        try {
            items = extractLabItems(text, apiKey);
        } catch (IOException e) {
            sendJson(exchange, 502, Map.of("success", false, "message", "解读服务连接失败，请稍后重试：" + e.getMessage()));
            return;
        }
        if (items.isEmpty()) {
            sendJson(exchange, Map.of("success", false,
                    "message", "没能从这段文字里读到检验项目。建议只拍「检验结果」那一块、让数字占满画面再试，或把项目逐行粘贴进来"));
            return;
        }

        Map<String, Object> analysis;
        try {
            analysis = interpretLabReport(items, username, memberId, apiKey);
        } catch (IOException e) {
            analysis = new LinkedHashMap<>();
            analysis.put("level", "amber");
            analysis.put("levelLabel", "需人工核对");
            analysis.put("summary", "检验数值已经读出，但这次解读服务没有响应。你可以先核对上方数值和箭头，稍后再点一次「开始解读」。");
            analysis.put("groups", List.of());
            analysis.put("drugWarnings", List.of());
            analysis.put("advice", List.of("带上这张报告单找医生当面看一次最稳妥"));
            analysis.put("recheck", "");
        }

        int abnormal = 0;
        boolean hasUnverified = false;
        for (Map<String, Object> item : items) {
            if (Boolean.TRUE.equals(item.get("abnormal"))) abnormal++;
            if (!Boolean.TRUE.equals(item.get("verified"))) hasUnverified = true;
        }

        String level = str(analysis.get("level")).trim();
        if (!"red".equals(level) && !"green".equals(level)) level = "amber";
        String summary = str(analysis.get("summary")).trim();

        int id = 0;
        String createdAt = "";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO lab_reports (username, member_id, raw_text, items_json, result_json, summary, level, abnormal_count) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setInt(2, memberId);
            stmt.setString(3, text);
            stmt.setString(4, toJson(Map.of("items", items)));
            stmt.setString(5, toJson(analysis));
            stmt.setString(6, summary.length() > 500 ? summary.substring(0, 500) : summary);
            stmt.setString(7, level);
            stmt.setInt(8, abnormal);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) id = keys.getInt(1);
            }
            try (PreparedStatement read = conn.prepareStatement(
                    "SELECT created_at FROM lab_reports WHERE id = ?")) {
                read.setInt(1, id);
                try (ResultSet rs = read.executeQuery()) {
                    if (rs.next()) createdAt = String.valueOf(rs.getTimestamp("created_at"));
                }
            }
        } catch (SQLException e) {
            // 存历史失败不影响这次解读
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("id", id);
        payload.put("createdAt", createdAt);
        payload.put("level", level);
        payload.put("levelLabel", str(analysis.get("levelLabel")));
        payload.put("summary", summary);
        payload.put("items", items);
        payload.put("abnormalCount", abnormal);
        payload.put("totalCount", items.size());
        payload.put("groups", analysis.get("groups"));
        payload.put("drugWarnings", analysis.get("drugWarnings"));
        payload.put("advice", analysis.get("advice"));
        payload.put("recheck", analysis.get("recheck"));
        payload.put("hasUnverified", hasUnverified);
        sendJson(exchange, payload);
    }

    /** 第一步：从 OCR 文字里抽检验项目，逐个数字回原文核对，对不上的标为待核对 */
    private static List<Map<String, Object>> extractLabItems(String text, String apiKey) throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是检验科医师，正在把一张化验单的 OCR 文字整理成结构化数据。\n");
        prompt.append("OCR 原文（可能有错行、缺字、表格列错位）：\n<<<\n").append(text).append("\n>>>\n");
        prompt.append("要求：\n");
        prompt.append("1. 只提取原文中确实出现的检验项目，给出 名称 / 结果 / 单位 / 参考范围 / 箭头\n");
        prompt.append("2. 结果值必须原样照抄，不要换算单位、不要四舍五入、不要推测缺失的数字\n");
        prompt.append("3. 忽略医院名称、患者姓名、科室、条形码、打印时间、页脚说明\n");
        prompt.append("4. 项目名用临床规范简称，如 白细胞计数、血红蛋白、肌酐、ALT、空腹血糖\n");
        prompt.append("5. 箭头只在原文出现 ↑ ↓ H L 时填写，填 ↑ 或 ↓，没有就留空字符串\n");
        prompt.append("6. 参考范围照抄原文，原文没有就留空\n");
        prompt.append("严格输出 JSON：{\"items\":[{\"name\":\"\",\"value\":\"\",\"unit\":\"\",\"range\":\"\",\"flag\":\"\"}],\"reportDate\":\"\"}");

        String raw = callDeepSeek(prompt.toString(), apiKey);
        if (raw == null) {
            throw new IOException("大模型接口没有返回内容，常见原因是账号余额不足、密钥失效或请求过于频繁");
        }
        Map<String, Object> parsed = parseLooseJson(raw);
        List<Map<String, Object>> items = new ArrayList<>();
        if (parsed == null || !(parsed.get("items") instanceof List<?> list)) {
            return items;
        }
        String compact = compactForMatch(text);
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            String name = str(map.get("name")).trim();
            String value = tidyLabText(str(map.get("value")).trim());
            if (name.isEmpty() || value.isEmpty()) continue;
            if (items.size() >= 60) break;

            String unit = tidyLabText(str(map.get("unit")).trim());
            String range = tidyLabText(str(map.get("range")).trim());
            String flag = str(map.get("flag")).trim();
            if (!"↑".equals(flag) && !"↓".equals(flag)) flag = "";

            // 数值必须能在 OCR 原文里找到，找不到就是模型编的，只展示不参与判断
            boolean verified = !compact.isEmpty() && compact.contains(compactForMatch(value));
            String computed = flagOf(value, range);
            String flagFrom = "computed";
            if (computed.isEmpty() && !verified) {
                // 参考范围不可用、数值又没核对上，只能沿用原单印的箭头
                computed = flag;
                if (!computed.isEmpty()) flagFrom = "original";
            }
            if (computed.isEmpty()) flagFrom = "none";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("value", value);
            item.put("unit", unit);
            item.put("range", range);
            item.put("flag", computed);
            item.put("flagFrom", flagFrom);
            item.put("abnormal", !computed.isEmpty());
            item.put("verified", verified);
            items.add(item);
        }
        return items;
    }

    /** 第二步：结合档案与在用药做解读 */
    private static Map<String, Object> interpretLabReport(List<Map<String, Object>> items, String username,
                                                          int memberId, String apiKey)
            throws IOException {
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> item : items) {
            rows.append(str(item.get("name"))).append(" ").append(str(item.get("value")));
            if (!str(item.get("unit")).isEmpty()) rows.append(" ").append(str(item.get("unit")));
            if (!str(item.get("range")).isEmpty()) rows.append("  参考范围 ").append(str(item.get("range")));
            if (!str(item.get("flag")).isEmpty()) rows.append("  ").append(str(item.get("flag")));
            if (!Boolean.TRUE.equals(item.get("verified"))) rows.append("  （此数值未在原文核对成功，可能有误）");
            rows.append("\n");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名全科医生，正在给患者解读化验单。\n");
        prompt.append("检验结果：\n").append(rows);
        try {
            PatientCtx ctx = loadPatientContext(username, memberId);
            prompt.append("患者：");
            prompt.append(ctx.age > 0 ? ctx.age + " 岁" : "年龄未填");
            prompt.append("，").append(ctx.gender.isBlank() ? "性别未填" : ctx.gender);
            prompt.append("；过敏史：").append(ctx.allergies.isBlank() ? "无记录" : ctx.allergies);
            prompt.append("；慢性病：").append(ctx.chronic.isBlank() ? "无记录" : ctx.chronic).append("\n");
            prompt.append("正在服用：").append(ctx.drugs.isEmpty() ? "无记录" : String.join("、", ctx.drugs)).append("\n");
        } catch (SQLException e) {
            prompt.append("患者档案：读取失败，按一般成年人处理\n");
        }
        prompt.append("要求：\n");
        prompt.append("1. level 只能取 red（建议尽快就医）、amber（需要复查或调整）、green（基本正常）\n");
        prompt.append("2. 按系统分组解读（肝功能 / 肾功能 / 血糖 / 血脂 / 血常规 / 尿常规等），每组 1-3 条要点，用大白话说清偏高偏低意味着什么\n");
        prompt.append("3. 必须结合在用药：某项异常与正在服的药有关时，写进 drugWarnings，说明风险，例如肾功能下降合并二甲双胍\n");
        prompt.append("4. advice 给 2-4 条能执行的建议；涉及停药一律写成「先咨询医生」，不要直接让人停药\n");
        prompt.append("5. 不做确诊，不写「你患有某某病」，可以说「提示」「需要排除」\n");
        prompt.append("严格输出 JSON：{\"level\":\"\",\"levelLabel\":\"不超过8字\",\"summary\":\"一句话总体判断，60字内\",")
                .append("\"groups\":[{\"system\":\"\",\"points\":[\"\"]}],\"drugWarnings\":[{\"drug\":\"\",\"text\":\"\"}],")
                .append("\"advice\":[\"\"],\"recheck\":\"一句话复查建议\"}");

        String raw = callDeepSeek(prompt.toString(), apiKey);
        Map<String, Object> parsed = parseLooseJson(raw);
        Map<String, Object> result = new LinkedHashMap<>();
        if (parsed == null) {
            result.put("level", "amber");
            result.put("levelLabel", "需人工核对");
            result.put("summary", "数值已读出，但解读结果解析失败，请稍后重试");
            result.put("groups", List.of());
            result.put("drugWarnings", List.of());
            result.put("advice", List.of());
            result.put("recheck", "");
            return result;
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        if (parsed.get("groups") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                String system = str(map.get("system")).trim();
                if (system.isEmpty()) continue;
                List<String> points = new ArrayList<>();
                if (map.get("points") instanceof List<?> pointList) {
                    for (Object point : pointList) {
                        String line = str(point).trim();
                        if (!line.isEmpty() && points.size() < 4) points.add(line);
                    }
                }
                if (points.isEmpty()) continue;
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("system", system);
                group.put("points", points);
                groups.add(group);
                if (groups.size() >= 6) break;
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        if (parsed.get("drugWarnings") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                String text = str(map.get("text")).trim();
                if (text.isEmpty()) continue;
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("drug", str(map.get("drug")).trim());
                warning.put("text", text);
                warnings.add(warning);
                if (warnings.size() >= 4) break;
            }
        }

        List<String> advice = new ArrayList<>();
        if (parsed.get("advice") instanceof List<?> list) {
            for (Object entry : list) {
                String line = str(entry).trim();
                if (!line.isEmpty() && advice.size() < 5) advice.add(line);
            }
        }

        result.put("level", str(parsed.get("level")).trim());
        result.put("levelLabel", str(parsed.get("levelLabel")).trim());
        result.put("summary", str(parsed.get("summary")).trim());
        result.put("groups", groups);
        result.put("drugWarnings", warnings);
        result.put("advice", advice);
        result.put("recheck", str(parsed.get("recheck")).trim());
        return result;
    }

    /** 把 OCR 常见的全角/变体写法还原成正常半角，用于界面展示 */
    private static String tidyLabText(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text.replace('．', '.').replace('·', '.').replace('•', '.').replace('∙', '.').replace('⋅', '.')
                .replace('－', '-').replace('—', '-').replace('–', '-').replace('−', '-')
                .replace('：', ':');
        // OCR 常把参考范围的连字符读成顿号或波浪号
        s = s.replaceAll("(?<=\\d)\\s*[、~～]\\s*(?=\\d)", "-");
        // 小数点被 OCR 读成中文逗号是常见情况（3，5 实际是 3.5）。
        // 只认「数字 + 逗号 + 1~2 位数字」这种形状，避免把 1,234 这类千分位改成小数
        s = s.replaceAll("(?<=\\d)\\s*[，,；;]\\s*(?=\\d{1,2}(?!\\d))", ".");
        return halfWidthDigits(s).trim();
    }

    /** 全角数字转半角 */
    private static String halfWidthDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0xFF10 && c <= 0xFF19) c = (char) (c - 0xFF10 + '0');
            sb.append(c);
        }
        return sb.toString();
    }

    /** 全角转半角并去掉空白，用于把模型抽出的数字回原文比对 */
    private static String compactForMatch(String value) {
        if (value == null) return "";
        // 离线 OCR 常把小数点读成中点、把范围里的连字符读成「一」，先在数字之间还原
        String prepared = halfWidthDigits(value)
                .replaceAll("(?<=\\d)\\s*[一－—–−~～]\\s*(?=\\d)", "-")
                .replaceAll("(?<=\\d)\\s*[·•∙⋅．]\\s*(?=\\d)", ".")
                // 同上：小数点被读成中文逗号
                .replaceAll("(?<=\\d)\\s*[，,；;]\\s*(?=\\d{1,2}(?!\\d))", ".");
        StringBuilder sb = new StringBuilder();
        for (char c : prepared.toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            if (c >= 0xFF10 && c <= 0xFF19) c = (char) (c - 0xFF10 + '0');
            if (c == '．' || c == '。' || c == '·' || c == '•' || c == '∙' || c == '⋅') c = '.';
            if (c == '－' || c == '—' || c == '–' || c == '−') c = '-';
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** 按参考范围判断高低，支持 44-106 / 44~106 / <5.0 / >90 几种写法 */
    private static String flagOf(String value, String range) {
        Double v = toDouble(value);
        if (v == null || range == null || range.isBlank()) return "";
        String r = compactForMatch(range);
        if (r.isEmpty()) return "";
        // 参考范围里混进字母（如 12S-3S0、130-17S）说明这一格被读花了，
        // 拿它算高低会误报，宁可不判，交给原文箭头和人工核对
        if (r.matches(".*[a-z].*")) return "";
        List<Double> nums = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (char c : r.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '.') {
                buf.append(c);
            } else {
                if (buf.length() > 0) {
                    try {
                        nums.add(Double.parseDouble(buf.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                    buf.setLength(0);
                }
            }
        }
        if (buf.length() > 0) {
            try {
                nums.add(Double.parseDouble(buf.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (nums.isEmpty()) return "";
        // 一个参考范围里正常只有 1~2 个数字。超过 2 个说明这一格被读花了
        // （例如 3,5-9,5 被切成 3 5 9 5），拿前两个当上下限会误判，宁可不判
        if (nums.size() > 2) return "";
        if (r.startsWith("<") || r.startsWith("≤") || r.startsWith("≦")) {
            return v > nums.get(0) * 1.0001 ? "↑" : "";
        }
        if (r.startsWith(">") || r.startsWith("≥") || r.startsWith("≧")) {
            return v < nums.get(0) * 0.9999 ? "↓" : "";
        }
        if (nums.size() >= 2) {
            double low = Math.min(nums.get(0), nums.get(1));
            double high = Math.max(nums.get(0), nums.get(1));
            // 上下限颠倒（如 130-175 被读成 130-17S）视为参考范围不可用
            if (nums.get(0) >= nums.get(1)) return "";
            if (v > high * 1.0001) return "↑";
            if (v < low * 0.9999) return "↓";
        }
        return "";
    }

    private static Double toDouble(String value) {
        if (value == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : compactForMatch(value).toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '.' || (c == '-' && sb.length() == 0)) {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            }
        }
        if (sb.length() == 0) return null;
        try {
            return Double.parseDouble(sb.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> listLabReports(String username, int memberId) throws SQLException {
        List<Map<String, Object>> reports = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, level, abnormal_count, summary, created_at FROM lab_reports "
                             + "WHERE username = ? AND member_id = ? ORDER BY id DESC LIMIT 20")) {
            stmt.setString(1, username);
            stmt.setInt(2, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("level", str(rs.getString("level")));
                    item.put("abnormalCount", rs.getInt("abnormal_count"));
                    item.put("summary", str(rs.getString("summary")));
                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    reports.add(item);
                }
            }
        }
        return Map.of("success", true, "reports", reports);
    }

    private static Map<String, Object> loadLabReport(String username, int id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, items_json, result_json, summary, level, abnormal_count, created_at "
                             + "FROM lab_reports WHERE id = ? AND username = ?")) {
            stmt.setInt(1, id);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Map.of("success", false, "message", "未找到这份报告，可能已被删除");
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("success", true);
                detail.put("id", rs.getInt("id"));
                Map<String, Object> result = parseJsonObject(str(rs.getString("result_json")));
                detail.put("level", str(rs.getString("level")));
                detail.put("levelLabel", str(result.get("levelLabel")));
                detail.put("summary", str(rs.getString("summary")));
                detail.put("groups", result.get("groups"));
                detail.put("drugWarnings", result.get("drugWarnings"));
                detail.put("advice", result.get("advice"));
                detail.put("recheck", result.get("recheck"));
                Map<String, Object> stored = parseJsonObject(str(rs.getString("items_json")));
                detail.put("items", stored.getOrDefault("items", List.of()));
                detail.put("abnormalCount", rs.getInt("abnormal_count"));
                detail.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                return detail;
            }
        }
    }

    /** 患者用药上下文：档案 + 在用药 + 规则表 */
    private static final class PatientCtx {
        final List<String> drugs = new ArrayList<>();      // 在用药（去重后）
        final List<String> expired = new ArrayList<>();
        final List<String[]> rules = new ArrayList<>();
        int age;
        String gender = "";
        String allergies = "";
        String chronic = "";
    }

    private static PatientCtx loadPatientContext(String username) throws SQLException {
        return loadPatientContext(username, 0);
    }

    private static PatientCtx loadPatientContext(String username, int memberId) throws SQLException {
        PatientCtx ctx = new PatientCtx();
        try (Connection conn = getConnection()) {
            if (memberId <= 0) memberId = defaultMemberId(conn, username);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT birth_date, gender, allergies, chronic FROM family_members WHERE id = ? AND owner = ?")) {
                stmt.setInt(1, memberId);
                stmt.setString(2, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int months = ageMonthsOf(rs.getString("birth_date"));
                        ctx.age = months < 0 ? 0 : months / 12;
                        ctx.gender = str(rs.getString("gender")).trim();
                        ctx.allergies = str(rs.getString("allergies")).trim();
                        ctx.chronic = str(rs.getString("chronic")).trim();
                    }
                }
            }
            // 成员没填生日时退回旧档案的年龄字段
            if (ctx.age <= 0) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT age FROM health_profiles WHERE username = ?")) {
                    stmt.setString(1, username);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) ctx.age = rs.getInt("age");
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT medicine_name, expire_date FROM cabinet_items WHERE username = ? AND member_id = ? ORDER BY id")) {
                stmt.setString(1, username);
                stmt.setInt(2, memberId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = str(rs.getString("medicine_name")).trim();
                        if (name.isEmpty()) continue;
                        if (!ctx.drugs.contains(name)) ctx.drugs.add(name);
                        Integer left = daysUntil(rs.getString("expire_date"));
                        if (left != null && left < 0 && !ctx.expired.contains(name)) ctx.expired.add(name);
                    }
                }
            }
            // 提醒/医嘱里有、但没录进药箱的药，同样算「在用药」
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT DISTINCT medicine_name FROM medication_plans WHERE username = ? AND member_id = ? AND active = 1")) {
                stmt.setString(1, username);
                stmt.setInt(2, memberId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = str(rs.getString("medicine_name")).trim();
                        if (!name.isEmpty() && !ctx.drugs.contains(name)) ctx.drugs.add(name);
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT ingredient, rule_type, target, level, message, advice FROM drug_rules")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ctx.rules.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getString(5), str(rs.getString(6))});
                    }
                }
            }
        }
        return ctx;
    }

    private static Map<String, Object> buildSafetyReport(String username, boolean refresh) throws SQLException {
        return buildSafetyReport(username, refresh, false);
    }

    private static Map<String, Object> buildSafetyReport(String username, boolean refresh, boolean skipAi)
            throws SQLException {
        return buildSafetyReport(username, refresh, skipAi, 0);
    }

    private static Map<String, Object> buildSafetyReport(String username, boolean refresh, boolean skipAi, int memberId)
            throws SQLException {
        PatientCtx ctx = loadPatientContext(username, memberId);
        List<String> drugs = ctx.drugs;
        List<String> expired = ctx.expired;
        List<String[]> rules = ctx.rules;
        int age = ctx.age;
        String gender = ctx.gender;
        String allergies = ctx.allergies;
        String chronic = ctx.chronic;

        List<String> chronicList = splitTerms(chronic);
        List<String> allergyList = splitTerms(allergies);
        List<Map<String, Object>> issues = new ArrayList<>();

        // 1. 同成分重复：感冒灵 + 对乙酰氨基酚 这类隐性重复
        Map<String, List<String>> byIngredient = new LinkedHashMap<>();
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (String drug : drugs) {
            for (String ingredient : ingredientsOf(drug)) {
                byIngredient.computeIfAbsent(ingredient, k -> new ArrayList<>()).add(drug);
                String cls = drugClassOf(ingredient);
                if (cls != null) byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(drug);
            }
        }
        for (Map.Entry<String, List<String>> entry : byIngredient.entrySet()) {
            List<String> hit = entry.getValue();
            if (hit.size() < 2) continue;
            issues.add(safetyIssue("red", "成分重复：" + entry.getKey(),
                    hit, "这些药都含「" + entry.getKey() + "」，同时服用等于加大剂量，容易造成肝肾负担或过量。",
                    "只保留一种，其余暂停；复方感冒药与退烧药尤其不要叠加。"));
        }
        for (Map.Entry<String, List<String>> entry : byClass.entrySet()) {
            List<String> hit = entry.getValue();
            if (hit.size() < 2) continue;
            // 同成分已单独报过的，这里不再重复提示
            if (issues.stream().anyMatch(i -> hit.equals(i.get("drugs")))) continue;
            issues.add(safetyIssue("amber", "同类药叠加：" + entry.getKey(),
                    hit, "这些药属于同一类（" + entry.getKey() + "），作用机制相似，叠加使用疗效不增加但副作用会累加。",
                    "一般不需要同时使用，建议咨询医生或药师后保留一种。"));
        }

        // 2. 过敏史冲突
        for (String allergy : allergyList) {
            List<String> keywords = new ArrayList<>();
            keywords.add(allergy);
            for (String[] row : ALLERGY_ALIAS) {
                if (row[0].equals(allergy)) {
                    for (int i = 1; i < row.length; i++) keywords.add(row[i]);
                }
            }
            List<String> hit = new ArrayList<>();
            for (String drug : drugs) {
                for (String keyword : keywords) {
                    if (keyword.length() >= 2 && drug.contains(keyword)) {
                        hit.add(drug);
                        break;
                    }
                }
            }
            if (hit.isEmpty()) continue;
            issues.add(safetyIssue("red", "过敏风险：" + allergy, hit,
                    "健康档案记录你对「" + allergy + "」过敏，药箱中的 " + String.join("、", hit) + " 属于同类或含该成分。",
                    "请立即停用并联系医生换药；如已服用且出现皮疹、呼吸困难，马上就医。"));
        }

        // 3. 慢病禁忌 + 特殊人群
        List<String> population = new ArrayList<>();
        if (age >= 65) population.add("老年人");
        if (age > 0 && age < 14) population.add("儿童");

        for (String[] rule : rules) {
            String ingredient = rule[0];
            String type = rule[1];
            String target = rule[2];
            List<String> hit = new ArrayList<>();
            for (String drug : drugs) {
                if (drug.contains(ingredient)) hit.add(drug);
            }
            if (hit.isEmpty()) continue;
            boolean matched;
            if ("contraindication".equals(type)) {
                matched = chronicList.stream().anyMatch(c -> c.contains(target) || target.contains(c));
            } else {
                matched = population.contains(target) || ("孕妇".equals(target) && chronic.contains("孕"));
            }
            if (!matched) continue;
            issues.add(safetyIssue(rule[3], target + "用药提示：" + ingredient, hit, rule[4], rule[5]));
        }

        // 4. 过期药品
        if (!expired.isEmpty()) {
            issues.add(safetyIssue("amber", "药箱有过期药品", expired,
                    String.join("、", expired) + " 已超过有效期，药效下降且可能变质。",
                    "建议及时清理，不要再服用；需要时重新购买。"));
        }

        // 5. AI 复核（规则表覆盖不到的罕见搭配）
        List<String> known = new ArrayList<>();
        for (Map<String, Object> issue : issues) known.add(str(issue.get("title")));
        StringBuilder profileLine = new StringBuilder("健康档案：");
        if (age > 0) profileLine.append("年龄 ").append(age).append(" 岁 ");
        if (!gender.isBlank()) profileLine.append("性别 ").append(gender).append(" ");
        profileLine.append("；过敏史：").append(allergyList.isEmpty() ? "无" : String.join("、", allergyList));
        profileLine.append("；慢性病/基础情况：").append(chronicList.isEmpty() ? "无" : String.join("、", chronicList));

        String cacheKey = username + "|" + String.join(",", drugs) + "|" + allergies + "|" + chronic + "|" + age;
        List<Map<String, Object>> aiIssues = null;
        Object[] cached = SAFETY_CACHE.get(cacheKey);
        if (!refresh && cached != null && System.currentTimeMillis() - (Long) cached[0] < SAFETY_CACHE_MS) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> value = (List<Map<String, Object>>) cached[1];
            aiIssues = value;
        }
        // null 表示模型没跑成功（无密钥/超时），空列表表示复核过但没有额外发现
        if (aiIssues == null && !skipAi) {
            aiIssues = aiSafetyIssues(drugs, profileLine.toString(), known);
            if (aiIssues != null) SAFETY_CACHE.put(cacheKey, new Object[]{System.currentTimeMillis(), aiIssues});
        }
        boolean aiUsed = aiIssues != null;
        if (aiIssues != null) {
            for (Map<String, Object> issue : aiIssues) {
                String title = str(issue.get("title"));
                boolean duplicated = issues.stream().anyMatch(i -> str(i.get("title")).equals(title));
                if (!duplicated) issues.add(issue);
            }
        }

        // 排序：red 在前，amber 在后
        issues.sort((a, b) -> Integer.compare("red".equals(str(a.get("level"))) ? 0 : 1,
                "red".equals(str(b.get("level"))) ? 0 : 1));

        int red = 0;
        int amber = 0;
        for (Map<String, Object> issue : issues) {
            if ("red".equals(str(issue.get("level")))) red++;
            else amber++;
        }
        int score = Math.max(0, 100 - red * 15 - amber * 6);
        String level = drugs.isEmpty() ? "none" : (red > 0 ? "red" : (amber > 0 ? "amber" : "green"));
        String levelLabel = drugs.isEmpty() ? "待评估"
                : (red > 0 ? "存在风险" : (amber > 0 ? "需注意" : "用药安全"));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("success", true);
        report.put("empty", drugs.isEmpty());
        report.put("score", score);
        report.put("level", level);
        report.put("levelLabel", levelLabel);
        report.put("issues", issues);
        report.put("aiUsed", aiUsed);
        report.put("stats", Map.of("drugs", drugs.size(), "red", red, "amber", amber,
                "ok", Math.max(0, drugs.size() - red - amber)));
        report.put("profile", Map.of("age", age, "gender", gender,
                "allergies", allergyList, "chronic", chronicList));
        report.put("summary", drugs.isEmpty()
                ? "药箱还没有药品，先录入正在服用的药才能做安全检查"
                : (issues.isEmpty() ? "已结合你的过敏史与慢性病情况检查 " + drugs.size() + " 种药，未发现明显冲突"
                : "共检查 " + drugs.size() + " 种药，发现 " + red + " 项需立即处理、" + amber + " 项需留意"));
        return report;
    }

    private static Map<String, Object> safetyIssue(String level, String title, List<String> drugs,
                                                   String reason, String advice) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", "red".equals(level) ? "red" : "amber");
        issue.put("title", title);
        issue.put("drugs", drugs);
        issue.put("reason", reason);
        issue.put("advice", advice);
        return issue;
    }

    /** 取出药品实际成分；无法识别时退回去掉剂型的药名 */
    private static List<String> ingredientsOf(String name) {
        List<String> out = new ArrayList<>();
        for (String[] row : INGREDIENT_ALIAS) {
            if (!name.contains(row[0])) continue;
            for (int i = 1; i < row.length; i++) {
                if (!out.contains(row[i])) out.add(row[i]);
            }
        }
        if (out.isEmpty() && name.length() >= 2) out.add(coreName(name));
        return out;
    }

    private static String drugClassOf(String ingredient) {
        for (String[] row : DRUG_CLASS) {
            if (row[0].equals(ingredient)) return row[1];
        }
        return null;
    }

    /** 把「高血压、糖尿病」或「高血压,糖尿病」拆成列表 */
    private static List<String> splitTerms(String value) {
        List<String> list = new ArrayList<>();
        if (value == null || value.isBlank()) return list;
        for (String part : value.split("[、,，;；/\\s]+")) {
            String item = part.trim();
            if (!item.isEmpty() && !"无".equals(item) && !"没有".equals(item)) list.add(item);
        }
        return list;
    }

    /** 交给模型补充规则表没覆盖的相互作用 */
    private static List<Map<String, Object>> aiSafetyIssues(List<String> drugs, String profileLine, List<String> known) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank() || drugs.isEmpty()) return null;

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名临床药师，正在为家庭药箱做用药安全审查。\n");
        prompt.append("在用药：").append(String.join("、", drugs)).append("\n");
        prompt.append(profileLine).append("\n");
        if (!known.isEmpty()) {
            prompt.append("规则引擎已发现（不要重复，也不要换个说法重述）：").append(String.join("；", known)).append("\n");
        }
        prompt.append("只补充规则引擎没覆盖、但确实存在的用药风险：药物相互作用、隐性重复成分、特殊人群风险。最多 3 条。");
        prompt.append("没有额外风险就返回空数组。不要提饮食建议、不要提就医建议以外的内容。\n");
        prompt.append("严格输出 JSON：{\"issues\":[{\"level\":\"red 或 amber\",\"title\":\"不超过14字的结论\",")
                .append("\"drugs\":[\"涉及药名\"],\"reason\":\"一句话原因\",\"advice\":\"一句话建议\"}]}");

        try {
            String raw = callDeepSeek(prompt.toString(), apiKey);
            Map<String, Object> parsed = parseLooseJson(raw);
            if (parsed == null) return null;
            List<Map<String, Object>> issues = new ArrayList<>();
            if (parsed.get("issues") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> map)) continue;
                    String title = str(map.get("title")).trim();
                    if (title.isEmpty()) continue;
                    List<String> hit = new ArrayList<>();
                    if (map.get("drugs") instanceof List<?> drugList) {
                        for (Object d : drugList) {
                            String name = str(d).trim();
                            if (!name.isEmpty() && hit.size() < 4) hit.add(name);
                        }
                    }
                    issues.add(safetyIssue(str(map.get("level")), title, hit,
                            str(map.get("reason")).trim(), str(map.get("advice")).trim()));
                    if (issues.size() >= 3) break;
                }
            }
            return issues;
        } catch (IOException e) {
            return null;
        }
    }

    /** 去掉剂型后缀，"对乙酰氨基酚缓释片" → "对乙酰氨基酚"，方便做重复用药比对 */
    private static String coreName(String name) {
        String[] suffixes = {"缓释片", "分散片", "肠溶片", "咀嚼片", "泡腾片", "口服液", "注射液",
                "软胶囊", "颗粒", "胶囊", "糖浆", "喷雾", "软膏", "滴眼液", "片", "丸"};
        for (String suffix : suffixes) {
            if (name.endsWith(suffix) && name.length() - suffix.length() >= 3) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private static Map<String, Object> parseLooseJson(String raw) {
        if (raw == null) return null;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        Map<String, Object> parsed = parseJsonObject(raw.substring(start, end + 1));
        return parsed.isEmpty() ? null : parsed;
    }

    private static String callDeepSeek(String prompt, String apiKey) throws IOException {
        String body = "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":" + quote(prompt) + "}],"
                + "\"temperature\":0.3,\"response_format\":{\"type\":\"json_object\"}}";
        URL url = new URL("https://api.deepseek.com/v1/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            return null;
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        String raw = response.toString();
        int choicesIndex = raw.indexOf("\"choices\"");
        if (choicesIndex < 0) {
            return null;
        }
        int messageIndex = raw.indexOf("\"message\"", choicesIndex);
        if (messageIndex < 0) {
            return null;
        }
        int contentIndex = raw.indexOf("\"content\"", messageIndex);
        if (contentIndex < 0) {
            return null;
        }
        int valueStart = raw.indexOf('"', contentIndex + 9);
        if (valueStart < 0) {
            return null;
        }
        return readJsonString(raw, valueStart + 1);
    }

    /** 从 JSON 文本里读出一个字符串值，正确处理引号、反斜杠、换行和 Unicode 转义 */
    private static String readJsonString(String raw, int from) {
        StringBuilder sb = new StringBuilder();
        int i = from;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                break;
            }
            if (ch == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 5 < raw.length()) {
                            try {
                                sb.append((char) Integer.parseInt(raw.substring(i + 2, i + 6), 16));
                                i += 6;
                                continue;
                            } catch (NumberFormatException ignored) {
                                sb.append(next);
                            }
                        } else {
                            sb.append(next);
                        }
                    }
                    default -> sb.append(next);
                }
                i += 2;
                continue;
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    /* ==================== 家庭药箱 / 用药提醒 / 收藏 / 浏览历史 ==================== */

    /** 距离过期还有多少天，解析失败返回 null */
    private static Integer daysUntil(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(), java.time.LocalDate.parse(date.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    /** 规范化 "8:00, 12:00 ,20:00"，非法项丢弃 */
    private static String normalizeTimes(String raw) {
        if (raw == null) return "";
        List<String> slots = new ArrayList<>();
        for (String part : raw.split("[,，;；\\s]+")) {
            String slot = part.trim();
            if (slot.isEmpty()) continue;
            if (!slot.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) continue;
            if (slot.length() == 4) slot = "0" + slot;
            if (!slots.contains(slot)) slots.add(slot);
        }
        slots.sort(String::compareTo);
        return String.join(",", slots);
    }

    /* ==================== 家庭成员 ==================== */

    /** 账号的默认成员；账号还没有任何成员时按档案补一条，保证每条数据都有归属 */
    private static int defaultMemberId(Connection conn, String username) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM family_members WHERE owner = ? AND is_default = 1 ORDER BY id LIMIT 1")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM family_members WHERE owner = ? ORDER BY id LIMIT 1")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE family_members SET is_default = 1 WHERE id = ?")) {
                        up.setInt(1, id);
                        up.executeUpdate();
                    }
                    return id;
                }
            }
        }
        String name = username;
        try (PreparedStatement read = conn.prepareStatement(
                "SELECT name FROM health_profiles WHERE username = ?")) {
            read.setString(1, username);
            try (ResultSet rs = read.executeQuery()) {
                if (rs.next() && !str(rs.getString("name")).isBlank()) name = str(rs.getString("name")).trim();
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO family_members (owner, name, relation, is_default, avatar_seed) VALUES (?, ?, '本人', 1, 'm0')",
                Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, username);
            ins.setString(2, name);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return 0;
    }

    /** 请求里带的 memberId 不可信：不是自己的成员就回落到默认成员 */
    private static int resolveMemberId(Connection conn, String username, String raw) throws SQLException {
        int asked = safeInt(raw, 0);
        if (asked > 0) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM family_members WHERE id = ? AND owner = ?")) {
                stmt.setInt(1, asked);
                stmt.setString(2, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return asked;
                }
            }
        }
        return defaultMemberId(conn, username);
    }

    /** 生日换算月龄，-1 表示生日不可用 */
    private static int ageMonthsOf(String birthDate) {
        if (birthDate == null || birthDate.length() < 10) return -1;
        try {
            LocalDate birth = LocalDate.parse(birthDate.substring(0, 10));
            LocalDate now = LocalDate.now();
            if (birth.isAfter(now)) return -1;
            int months = (now.getYear() - birth.getYear()) * 12 + (now.getMonthValue() - birth.getMonthValue());
            if (now.getDayOfMonth() < birth.getDayOfMonth()) months--;
            return Math.max(0, months);
        } catch (Exception e) {
            return -1;
        }
    }

    private static Double numOrNull(Object raw) {
        String s = str(raw).trim();
        if (s.isEmpty()) return null;
        try {
            double v = Double.parseDouble(s);
            return Double.isFinite(v) && v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> memberMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        int id = rs.getInt("id");
        String birth = rs.getString("birth_date");
        int months = ageMonthsOf(birth);
        m.put("id", id);
        m.put("name", str(rs.getString("name")));
        m.put("relation", str(rs.getString("relation")));
        m.put("gender", str(rs.getString("gender")));
        m.put("birthDate", birth == null ? "" : birth.substring(0, 10));
        m.put("age", months < 0 ? 0 : months / 12);
        m.put("ageMonths", months);
        m.put("isChild", months >= 0 && months < 144);
        m.put("heightCm", str(rs.getString("height_cm")));
        m.put("weightKg", str(rs.getString("weight_kg")));
        m.put("bloodType", str(rs.getString("blood_type")));
        m.put("allergies", str(rs.getString("allergies")));
        m.put("chronic", str(rs.getString("chronic")));
        m.put("pregnant", str(rs.getString("pregnant")));
        m.put("egfr", str(rs.getString("egfr")));
        m.put("alt", str(rs.getString("alt_value")));
        m.put("renalStage", str(rs.getString("renal_stage")));
        m.put("avatarSeed", str(rs.getString("avatar_seed")));
        m.put("isDefault", rs.getInt("is_default") == 1);
        return m;
    }

    private static void handleMembers(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String action = str(request.get("action")).trim();
            String username = str(request.get("username")).trim();
            if (username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请先登录"));
                return;
            }
            try (Connection conn = getConnection()) {
                if ("delete".equals(action)) {
                    handleDeleteMember(exchange, conn, username, request);
                    return;
                }
                if ("default".equals(action)) {
                    int id = safeInt(request.get("id"), 0);
                    try (PreparedStatement clear = conn.prepareStatement(
                            "UPDATE family_members SET is_default = 0 WHERE owner = ?")) {
                        clear.setString(1, username);
                        clear.executeUpdate();
                    }
                    try (PreparedStatement set = conn.prepareStatement(
                            "UPDATE family_members SET is_default = 1 WHERE id = ? AND owner = ?")) {
                        set.setInt(1, id);
                        set.setString(2, username);
                        if (set.executeUpdate() == 0) {
                            sendJson(exchange, 404, Map.of("success", false, "message", "成员不存在"));
                            return;
                        }
                    }
                    sendJson(exchange, Map.of("success", true, "message", "已切换默认成员"));
                    return;
                }
                saveMember(exchange, conn, username, request);
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "成员操作失败：" + e.getMessage()));
            }
            return;
        }

        String username = str(readQueryParam(exchange, "username")).trim();
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "members", List.of(), "currentId", 0));
            return;
        }
        List<Map<String, Object>> members = new ArrayList<>();
        try (Connection conn = getConnection()) {
            int current = defaultMemberId(conn, username);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT m.*, "
                            + "(SELECT COUNT(*) FROM cabinet_items c WHERE c.member_id = m.id) AS cabinet_count, "
                            + "(SELECT COUNT(*) FROM medication_plans p WHERE p.member_id = m.id AND p.active = 1) AS plan_count, "
                            + "(SELECT COUNT(*) FROM lab_reports r WHERE r.member_id = m.id) AS lab_count "
                            + "FROM family_members m WHERE m.owner = ? ORDER BY m.is_default DESC, m.id")) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = memberMap(rs);
                        m.put("cabinetCount", rs.getInt("cabinet_count"));
                        m.put("planCount", rs.getInt("plan_count"));
                        m.put("labCount", rs.getInt("lab_count"));
                        members.add(m);
                    }
                }
            }
            sendJson(exchange, Map.of("success", true, "members", members, "currentId", current));
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "成员读取失败：" + e.getMessage()));
        }
    }

    private static void saveMember(HttpExchange exchange, Connection conn, String username,
                                   Map<String, Object> request) throws IOException, SQLException {
        int id = safeInt(request.get("id"), 0);
        String name = str(request.get("name")).trim();
        if (name.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "请填写姓名"));
            return;
        }
        if (name.length() > 20) {
            sendJson(exchange, 400, Map.of("success", false, "message", "姓名太长了"));
            return;
        }
        String relation = str(request.get("relation")).trim();
        if (relation.isEmpty()) relation = id > 0 ? "本人" : "其他";
        if (!List.of("本人", "配偶", "父亲", "母亲", "子女", "其他").contains(relation)) relation = "其他";

        String gender = str(request.get("gender")).trim();
        if (!gender.isEmpty() && !"男".equals(gender) && !"女".equals(gender)) gender = "";

        String birthRaw = str(request.get("birthDate")).trim();
        String birth = "";
        if (!birthRaw.isEmpty()) {
            try {
                LocalDate d = LocalDate.parse(birthRaw.substring(0, Math.min(10, birthRaw.length())).replace('/', '-'),
                        DateTimeFormatter.ISO_LOCAL_DATE);
                if (d.isAfter(LocalDate.now())) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "生日不能晚于今天"));
                    return;
                }
                if (d.getYear() < 1900) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "生日看起来不对，请重新填写"));
                    return;
                }
                birth = d.toString();
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("success", false, "message", "生日格式应为 1990-05-12"));
                return;
            }
        }

        Double height = numOrNull(request.get("heightCm"));
        Double weight = numOrNull(request.get("weightKg"));
        if (height != null && (height < 30 || height > 250)) {
            sendJson(exchange, 400, Map.of("success", false, "message", "身高请填 30~250 厘米"));
            return;
        }
        if (weight != null && (weight < 1 || weight > 300)) {
            sendJson(exchange, 400, Map.of("success", false, "message", "体重请填 1~300 公斤"));
            return;
        }

        String pregnant = str(request.get("pregnant")).trim();
        if (pregnant.isEmpty()) pregnant = "无";
        if (!List.of("无", "备孕", "孕早期", "孕中期", "孕晚期", "哺乳期").contains(pregnant)) pregnant = "无";
        if ("男".equals(gender)) pregnant = "无";

        Double egfr = numOrNull(request.get("egfr"));
        Double alt = numOrNull(request.get("alt"));
        String renalStage = "";
        if (egfr != null) {
            if (egfr >= 90) renalStage = "G1";
            else if (egfr >= 60) renalStage = "G2";
            else if (egfr >= 45) renalStage = "G3a";
            else if (egfr >= 30) renalStage = "G3b";
            else if (egfr >= 15) renalStage = "G4";
            else renalStage = "G5";
        }

        String allergies = str(request.get("allergies")).trim();
        String chronic = str(request.get("chronic")).trim();
        String bloodType = str(request.get("bloodType")).trim();

        if (id > 0) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE family_members SET name = ?, relation = ?, gender = ?, birth_date = ?, height_cm = ?, "
                            + "weight_kg = ?, blood_type = ?, allergies = ?, chronic = ?, pregnant = ?, egfr = ?, "
                            + "alt_value = ?, renal_stage = ? WHERE id = ? AND owner = ?")) {
                stmt.setString(1, name);
                stmt.setString(2, relation);
                stmt.setString(3, gender.isEmpty() ? null : gender);
                stmt.setString(4, birth.isEmpty() ? null : birth);
                setNullableDecimal(stmt, 5, height);
                setNullableDecimal(stmt, 6, weight);
                stmt.setString(7, bloodType.isEmpty() ? null : bloodType);
                stmt.setString(8, allergies.isEmpty() ? null : allergies);
                stmt.setString(9, chronic.isEmpty() ? null : chronic);
                stmt.setString(10, pregnant);
                setNullableDecimal(stmt, 11, egfr);
                setNullableDecimal(stmt, 12, alt);
                stmt.setString(13, renalStage.isEmpty() ? null : renalStage);
                stmt.setInt(14, id);
                stmt.setString(15, username);
                if (stmt.executeUpdate() == 0) {
                    sendJson(exchange, 404, Map.of("success", false, "message", "成员不存在"));
                    return;
                }
            }
            sendJson(exchange, Map.of("success", true, "id", id, "message", "已保存"));
            return;
        }

        int existing = 0;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM family_members WHERE owner = ?")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) existing = rs.getInt(1);
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO family_members (owner, name, relation, gender, birth_date, height_cm, weight_kg, "
                        + "blood_type, allergies, chronic, pregnant, egfr, alt_value, renal_stage, is_default, avatar_seed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, name);
            stmt.setString(3, relation);
            stmt.setString(4, gender.isEmpty() ? null : gender);
            stmt.setString(5, birth.isEmpty() ? null : birth);
            setNullableDecimal(stmt, 6, height);
            setNullableDecimal(stmt, 7, weight);
            stmt.setString(8, bloodType.isEmpty() ? null : bloodType);
            stmt.setString(9, allergies.isEmpty() ? null : allergies);
            stmt.setString(10, chronic.isEmpty() ? null : chronic);
            stmt.setString(11, pregnant);
            setNullableDecimal(stmt, 12, egfr);
            setNullableDecimal(stmt, 13, alt);
            stmt.setString(14, renalStage.isEmpty() ? null : renalStage);
            stmt.setInt(15, existing == 0 ? 1 : 0);
            stmt.setString(16, "m" + existing);
            stmt.executeUpdate();
            int newId = 0;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) newId = keys.getInt(1);
            }
            sendJson(exchange, Map.of("success", true, "id", newId, "message", "已添加家庭成员"));
        }
    }

    private static void setNullableDecimal(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null) stmt.setNull(index, java.sql.Types.DECIMAL);
        else stmt.setDouble(index, value);
    }

    private static void handleDeleteMember(HttpExchange exchange, Connection conn, String username,
                                           Map<String, Object> request) throws IOException, SQLException {
        int id = safeInt(request.get("id"), 0);
        String name;
        int isDefault;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name, is_default FROM family_members WHERE id = ? AND owner = ?")) {
            stmt.setInt(1, id);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    sendJson(exchange, 404, Map.of("success", false, "message", "成员不存在"));
                    return;
                }
                name = str(rs.getString("name"));
                isDefault = rs.getInt("is_default");
            }
        }
        int total;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM family_members WHERE owner = ?")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                total = rs.next() ? rs.getInt(1) : 0;
            }
        }
        if (total <= 1) {
            sendJson(exchange, 400, Map.of("success", false, "message", "至少要保留一位家庭成员"));
            return;
        }
        String confirm = str(request.get("confirmName")).trim();
        if (!confirm.equals(name)) {
            sendJson(exchange, 400, Map.of("success", false, "message", "请填写要删除成员的姓名以确认"));
            return;
        }

        Map<String, Object> deleted = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE l FROM medication_logs l JOIN medication_plans p ON l.plan_id = p.id WHERE p.member_id = ?")) {
            stmt.setInt(1, id);
            deleted.put("logs", stmt.executeUpdate());
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM medication_plans WHERE member_id = ?")) {
            stmt.setInt(1, id);
            deleted.put("plans", stmt.executeUpdate());
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM medication_logs WHERE member_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM cabinet_items WHERE member_id = ?")) {
            stmt.setInt(1, id);
            deleted.put("cabinet", stmt.executeUpdate());
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM lab_reports WHERE member_id = ?")) {
            stmt.setInt(1, id);
            deleted.put("labs", stmt.executeUpdate());
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM consultations WHERE member_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM family_members WHERE id = ? AND owner = ?")) {
            stmt.setInt(1, id);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
        if (isDefault == 1) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE family_members SET is_default = 1 WHERE owner = ? ORDER BY id LIMIT 1")) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            }
        }
        sendJson(exchange, Map.of("success", true, "deleted", deleted, "message", "已删除 " + name));
    }

    private static void handleCabinet(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String action = str(request.get("action")).trim();
            String username = str(request.get("username")).trim();
            if (username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请先登录"));
                return;
            }
            int id = safeInt(request.get("id"), 0);
            try (Connection conn = getConnection()) {
                int memberId = resolveMemberId(conn, username, str(request.get("memberId")));
                if ("delete".equals(action)) {
                    // 先清打卡记录（依赖计划表），再删计划，最后删药箱条目
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE l FROM medication_logs l JOIN medication_plans p ON l.plan_id = p.id " +
                            "WHERE p.cabinet_id = ? AND p.username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM medication_plans WHERE cabinet_id = ? AND username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM cabinet_items WHERE id = ? AND username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    sendJson(exchange, Map.of("success", true, "message", "已从药箱移除"));
                    return;
                }

                String name = str(request.get("medicineName")).trim();
                if (name.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请填写药品名称"));
                    return;
                }
                String spec = str(request.get("spec")).trim();
                int quantity = safeInt(request.get("quantity"), 1);
                if (quantity < 0) quantity = 0;
                String expire = str(request.get("expireDate")).trim();
                String note = str(request.get("note")).trim();
                String dosage = str(request.get("dosage")).trim();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO cabinet_items (username, member_id, medicine_name, spec, quantity, expire_date, note, dosage) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), " +
                        "expire_date = VALUES(expire_date), note = VALUES(note), dosage = VALUES(dosage)")) {
                    stmt.setString(1, username);
                    stmt.setInt(2, memberId);
                    stmt.setString(3, name);
                    stmt.setString(4, spec);
                    stmt.setInt(5, quantity);
                    stmt.setString(6, expire.isEmpty() ? null : expire);
                    stmt.setString(7, note.isEmpty() ? null : note);
                    stmt.setString(8, dosage.isEmpty() ? null : dosage);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true, "message", "已保存到药箱：" + name));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "药箱保存失败：" + e.getMessage()));
            }
            return;
        }

        String username = str(readQueryParam(exchange, "username")).trim();
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "items", List.of(), "count", 0));
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int expiring = 0;
        try (Connection conn = getConnection()) {
            int memberId = resolveMemberId(conn, username, readQueryParam(exchange, "memberId"));
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, medicine_name, spec, quantity, expire_date, note, dosage, " +
                    "DATE_FORMAT(created_at, '%Y-%m-%d') AS created FROM cabinet_items " +
                    "WHERE username = ? AND member_id = ? ORDER BY id DESC")) {
                stmt.setString(1, username);
                stmt.setInt(2, memberId);
                try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    String expire = rs.getString("expire_date");
                    Integer left = daysUntil(expire);
                    String status = "正常";
                    if (left != null) {
                        if (left < 0) status = "已过期";
                        else if (left <= 30) status = "即将过期";
                    }
                    if (!"正常".equals(status)) expiring++;
                    item.put("id", rs.getInt("id"));
                    item.put("medicineName", rs.getString("medicine_name"));
                    item.put("spec", rs.getString("spec"));
                    item.put("quantity", rs.getInt("quantity"));
                    item.put("expireDate", expire);
                    item.put("daysLeft", left);
                    item.put("status", status);
                    item.put("note", rs.getString("note"));
                    item.put("dosage", rs.getString("dosage"));
                    item.put("createdAt", rs.getString("created"));
                    items.add(item);
                }
            }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "药箱读取失败"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "items", items, "count", items.size(), "expiring", expiring));
    }

    private static void handlePlans(HttpExchange exchange) throws IOException {
        String isoToday = today();

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String action = str(request.get("action")).trim();
            String username = str(request.get("username")).trim();
            if (username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请先登录"));
                return;
            }
            int id = safeInt(request.get("id"), 0);
            int planId = safeInt(request.get("planId"), id);
            String slot = str(request.get("slot")).trim();
            String date = str(request.get("date")).trim();
            if (date.isBlank()) date = isoToday;

            try (Connection conn = getConnection()) {
                int memberId = resolveMemberId(conn, username, str(request.get("memberId")));
                if ("delete".equals(action)) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM medication_plans WHERE id = ? AND username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM medication_logs WHERE plan_id = ? AND username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    sendJson(exchange, Map.of("success", true, "message", "已删除该提醒"));
                    return;
                }
                if ("checkin".equals(action) || "undo".equals(action)) {
                    if (planId <= 0 || slot.isBlank()) {
                        sendJson(exchange, 400, Map.of("success", false, "message", "缺少计划或时间点"));
                        return;
                    }
                    if ("checkin".equals(action)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "INSERT IGNORE INTO medication_logs (plan_id, username, member_id, take_date, slot) VALUES (?, ?, ?, ?, ?)")) {
                            stmt.setInt(1, planId);
                            stmt.setString(2, username);
                            stmt.setInt(3, memberId);
                            stmt.setString(4, date);
                            stmt.setString(5, slot);
                            stmt.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "DELETE FROM medication_logs WHERE plan_id = ? AND username = ? AND take_date = ? AND slot = ?")) {
                            stmt.setInt(1, planId);
                            stmt.setString(2, username);
                            stmt.setString(3, date);
                            stmt.setString(4, slot);
                            stmt.executeUpdate();
                        }
                    }
                    sendJson(exchange, Map.of("success", true,
                            "message", "checkin".equals(action) ? "已打卡" : "已取消打卡"));
                    return;
                }
                if ("toggle".equals(action)) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE medication_plans SET active = 1 - active WHERE id = ? AND username = ?")) {
                        stmt.setInt(1, id);
                        stmt.setString(2, username);
                        stmt.executeUpdate();
                    }
                    sendJson(exchange, Map.of("success", true, "message", "已切换提醒状态"));
                    return;
                }

                String name = str(request.get("medicineName")).trim();
                String times = normalizeTimes(str(request.get("times")));
                if (name.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请填写药品名称"));
                    return;
                }
                if (times.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "请至少填写一个提醒时间，如 08:00"));
                    return;
                }
                String dosage = str(request.get("dosage")).trim();
                String startDate = str(request.get("startDate")).trim();
                String endDate = str(request.get("endDate")).trim();
                if (startDate.isBlank()) startDate = isoToday;
                int cabinetId = safeInt(request.get("cabinetId"), 0);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO medication_plans (username, member_id, cabinet_id, medicine_name, dosage, times, start_date, end_date, active) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)")) {
                    stmt.setString(1, username);
                    stmt.setInt(2, memberId);
                    if (cabinetId > 0) stmt.setInt(3, cabinetId); else stmt.setNull(3, java.sql.Types.INTEGER);
                    stmt.setString(4, name);
                    stmt.setString(5, dosage);
                    stmt.setString(6, times);
                    stmt.setString(7, startDate);
                    stmt.setString(8, endDate.isEmpty() ? null : endDate);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true, "message", "提醒已添加：" + times.replace(",", " / ")));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "提醒保存失败：" + e.getMessage()));
            }
            return;
        }

        String username = str(readQueryParam(exchange, "username")).trim();
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "plans", List.of(), "today", Map.of("total", 0, "taken", 0, "pending", 0)));
            return;
        }

        Map<Integer, Map<String, Object>> planMap = new LinkedHashMap<>();
        try (Connection conn = getConnection()) {
            int memberId = resolveMemberId(conn, username, readQueryParam(exchange, "memberId"));
            try (PreparedStatement stmt = conn.prepareStatement(
                     "SELECT p.id, p.medicine_name, p.dosage, p.times, p.start_date, p.end_date, p.active, " +
                     "p.source, p.doctor_name, p.note, " +
                     "DATE_FORMAT(p.created_at, '%Y-%m-%d') AS created, l.slot AS taken_slot " +
                     "FROM medication_plans p LEFT JOIN medication_logs l " +
                     "ON l.plan_id = p.id AND l.take_date = ? WHERE p.username = ? AND p.member_id = ? " +
                     "ORDER BY p.active DESC, p.id DESC")) {
            stmt.setString(1, isoToday);
            stmt.setString(2, username);
            stmt.setInt(3, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt("id");
                    Map<String, Object> plan = planMap.get(pid);
                    if (plan == null) {
                        plan = new LinkedHashMap<>();
                        plan.put("id", pid);
                        plan.put("medicineName", rs.getString("medicine_name"));
                        plan.put("dosage", rs.getString("dosage"));
                        plan.put("times", List.of(rs.getString("times").split(",")));
                        plan.put("rawTimes", rs.getString("times"));
                        plan.put("startDate", rs.getString("start_date"));
                        plan.put("endDate", rs.getString("end_date"));
                        plan.put("active", rs.getInt("active") == 1);
                        plan.put("source", str(rs.getString("source")));
                        plan.put("doctorName", str(rs.getString("doctor_name")));
                        plan.put("note", str(rs.getString("note")));
                        plan.put("createdAt", rs.getString("created"));
                        plan.put("takenSlots", new ArrayList<String>());
                        planMap.put(pid, plan);
                    }
                    String taken = rs.getString("taken_slot");
                    if (taken != null) {
                        @SuppressWarnings("unchecked")
                        List<String> takenSlots = (List<String>) plan.get("takenSlots");
                        takenSlots.add(taken);
                    }
                }
            }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "提醒读取失败"));
            return;
        }

        List<Map<String, Object>> plans = new ArrayList<>(planMap.values());
        List<Map<String, Object>> doses = new ArrayList<>();
        int total = 0;
        int taken = 0;
        for (Map<String, Object> plan : plans) {
            if (!Boolean.TRUE.equals(plan.get("active"))) continue;
            String startDate = str(plan.get("startDate")).trim();
            String endDate = str(plan.get("endDate")).trim();
            if (!startDate.isBlank() && isoToday.compareTo(startDate) < 0) continue;
            if (!endDate.isBlank() && isoToday.compareTo(endDate) > 0) continue;
            @SuppressWarnings("unchecked")
            List<String> slots = (List<String>) plan.get("times");
            @SuppressWarnings("unchecked")
            List<String> takenSlots = (List<String>) plan.get("takenSlots");
            for (String slot : slots) {
                boolean done = takenSlots.contains(slot);
                total++;
                if (done) taken++;
                Map<String, Object> dose = new LinkedHashMap<>();
                dose.put("planId", plan.get("id"));
                dose.put("medicineName", plan.get("medicineName"));
                dose.put("dosage", plan.get("dosage"));
                dose.put("slot", slot);
                dose.put("taken", done);
                dose.put("source", plan.get("source"));
                dose.put("doctorName", plan.get("doctorName"));
                doses.add(dose);
            }
        }
        doses.sort((a, b) -> str(a.get("slot")).compareTo(str(b.get("slot"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("date", isoToday);
        summary.put("total", total);
        summary.put("taken", taken);
        summary.put("pending", total - taken);
        summary.put("rate", total == 0 ? 0 : Math.round(taken * 100.0 / total));
        sendJson(exchange, Map.of("success", true, "plans", plans, "today", summary, "doses", doses));
    }

    private static void handleFavorites(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String action = str(request.get("action")).trim();
            String username = str(request.get("username")).trim();
            String type = str(request.get("type")).trim();
            String targetId = str(request.get("targetId")).trim();
            String title = str(request.get("title")).trim();
            if (username.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "请先登录"));
                return;
            }
            if (targetId.isBlank() || (!"medicine".equals(type) && !"disease".equals(type))) {
                sendJson(exchange, 400, Map.of("success", false, "message", "收藏对象无效"));
                return;
            }
            try (Connection conn = getConnection()) {
                boolean exists;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id FROM favorites WHERE username = ? AND target_type = ? AND target_id = ?")) {
                    stmt.setString(1, username);
                    stmt.setString(2, type);
                    stmt.setString(3, targetId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        exists = rs.next();
                    }
                }
                boolean wantRemove = "remove".equals(action) || ("toggle".equals(action) && exists);
                if (wantRemove) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM favorites WHERE username = ? AND target_type = ? AND target_id = ?")) {
                        stmt.setString(1, username);
                        stmt.setString(2, type);
                        stmt.setString(3, targetId);
                        stmt.executeUpdate();
                    }
                    sendJson(exchange, Map.of("success", true, "favorited", false, "message", "已取消收藏"));
                    return;
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT IGNORE INTO favorites (username, target_type, target_id, title) VALUES (?, ?, ?, ?)")) {
                    stmt.setString(1, username);
                    stmt.setString(2, type);
                    stmt.setString(3, targetId);
                    stmt.setString(4, title.isEmpty() ? targetId : title);
                    stmt.executeUpdate();
                }
                sendJson(exchange, Map.of("success", true, "favorited", true, "message", "已加入收藏"));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("success", false, "message", "收藏操作失败：" + e.getMessage()));
            }
            return;
        }

        String username = str(readQueryParam(exchange, "username")).trim();
        String type = str(readQueryParam(exchange, "type")).trim();
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "favorites", List.of(), "count", 0));
            return;
        }
        String sql = "SELECT id, target_type, target_id, title, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created " +
                "FROM favorites WHERE username = ?" + (type.isBlank() ? "" : " AND target_type = ?") + " ORDER BY id DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            if (!type.isBlank()) stmt.setString(2, type);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("type", rs.getString("target_type"));
                    item.put("targetId", rs.getString("target_id"));
                    item.put("title", rs.getString("title"));
                    item.put("createdAt", rs.getString("created"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "收藏读取失败"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "favorites", list, "count", list.size()));
    }

    private static void handleHistory(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> request = readJson(exchange);
            String username = str(request.get("username")).trim();
            String type = str(request.get("type")).trim();
            String targetId = str(request.get("targetId")).trim();
            String title = str(request.get("title")).trim();
            if (username.isBlank() || targetId.isBlank()) {
                sendJson(exchange, Map.of("success", true, "message", "未记录"));
                return;
            }
            if (!"medicine".equals(type) && !"disease".equals(type)) {
                sendJson(exchange, 400, Map.of("success", false, "message", "类型无效"));
                return;
            }
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO browse_history (username, target_type, target_id, title) VALUES (?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE title = VALUES(title), viewed_at = CURRENT_TIMESTAMP")) {
                stmt.setString(1, username);
                stmt.setString(2, type);
                stmt.setString(3, targetId);
                stmt.setString(4, title.isEmpty() ? targetId : title);
                stmt.executeUpdate();
            } catch (SQLException e) {
                sendJson(exchange, Map.of("success", false, "message", "浏览记录写入失败"));
                return;
            }
            sendJson(exchange, Map.of("success", true, "message", "已记录"));
            return;
        }

        String username = str(readQueryParam(exchange, "username")).trim();
        int limit = safeInt(readQueryParam(exchange, "limit"), 30);
        if (limit <= 0 || limit > 200) limit = 30;
        if (username.isBlank()) {
            sendJson(exchange, Map.of("success", true, "items", List.of(), "count", 0));
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, target_type, target_id, title, DATE_FORMAT(viewed_at, '%Y-%m-%d %H:%i') AS viewed " +
                     "FROM browse_history WHERE username = ? ORDER BY viewed_at DESC LIMIT ?")) {
            stmt.setString(1, username);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("type", rs.getString("target_type"));
                    item.put("targetId", rs.getString("target_id"));
                    item.put("title", rs.getString("title"));
                    item.put("viewedAt", rs.getString("viewed"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            sendJson(exchange, 500, Map.of("success", false, "message", "浏览历史读取失败"));
            return;
        }
        sendJson(exchange, Map.of("success", true, "items", list, "count", list.size()));
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String fileName = requestPath == null || "/".equals(requestPath) ? "/index.html" : requestPath;
        String relativePath = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        Path filePath = Path.of(System.getProperty("user.dir"), relativePath).normalize();

        if (filePath.toString().contains("..") || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            if (requestPath.startsWith("/api/")) {
                sendJson(exchange, 404, Map.of("success", false, "message", "API not found"));
            } else {
                sendJson(exchange, 404, Map.of("success", false, "message", "Page not found"));
            }
            return;
        }

        byte[] bytes = Files.readAllBytes(filePath);
        String contentType = detectContentType(fileName);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String detectContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html;charset=utf-8";
        if (fileName.endsWith(".css")) return "text/css;charset=utf-8";
        if (fileName.endsWith(".js")) return "application/javascript;charset=utf-8";
        if (fileName.endsWith(".json")) return "application/json;charset=utf-8";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange exchange, Object payload) throws IOException {
        sendJson(exchange, 200, payload);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] response = toJson(payload).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json;charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static Map<String, Object> medicine(String name, String category, String use, String price) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("category", category);
        item.put("use", use);
        item.put("price", price);
        return item;
    }

    private static Map<String, Object> disease(String name, String tag, String description) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("tag", tag);
        item.put("description", description);
        return item;
    }

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw == null || raw.isBlank()) {
            return new HashMap<>();
        }
        return parseJsonObject(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String raw) {
        Map<String, Object> data = new HashMap<>();
        raw = raw.trim();
        if (raw.startsWith("{") && raw.endsWith("}")) {
            String inner = raw.substring(1, raw.length() - 1).trim();
            if (!inner.isEmpty()) {
                String[] pairs = splitJsonPairs(inner);
                for (String pair : pairs) {
                    String[] kv = splitJsonKeyValue(pair);
                    if (kv.length == 2) {
                        data.put(kv[0], parseJsonValue(kv[1]));
                    }
                }
            }
        }
        return data;
    }

    private static String[] splitJsonPairs(String source) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString && ch == '\\' && i + 1 < source.length()) {
                i++;   // 跳过被转义的字符，避免把 \" 当成字符串结束
                continue;
            }
            if (ch == '"') {
                inString = !inString;
            } else if (!inString) {
                if (ch == '{' || ch == '[') depth++;
                else if (ch == '}' || ch == ']') depth--;
                else if (ch == ',' && depth == 0) {
                    result.add(source.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        if (start < source.length()) {
            result.add(source.substring(start).trim());
        }
        return result.toArray(new String[0]);
    }

    private static String[] splitJsonKeyValue(String pair) {
        int colon = pair.indexOf(':');
        if (colon < 0) {
            return new String[0];
        }
        String key = pair.substring(0, colon).trim();
        String value = pair.substring(colon + 1).trim();
        return new String[]{stripJsonQuotes(key), value};
    }

    @SuppressWarnings("unchecked")
    private static Object parseJsonValue(String value) {
        if (value == null) return null;
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            if (inner.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> list = new ArrayList<>();
            for (String item : splitJsonPairs(inner)) {
                if (item == null || item.isBlank()) continue;
                list.add(parseJsonValue(item));
            }
            return list;
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return parseJsonObject(value);
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unescapeJson(value.substring(1, value.length() - 1));
        }
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        if ("null".equalsIgnoreCase(value)) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored2) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ignored3) {
                    return value;
                }
            }
        }
    }

    private static String stripJsonQuotes(String value) {
        return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    }

    private static String unescapeJson(String value) {
        if (value.indexOf('\\') < 0) return value;
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                sb.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'u': {
                    // 不少客户端（Python、Java 的默认 JSON 序列化）会把中文拆成 Unicode 转义，
                    // 这里必须还原，否则收到的正文全是转义符
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        sb.append("\\u");
                    }
                    break;
                }
                default: sb.append(next);
            }
        }
        return sb.toString();
    }

    private static List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static int safeInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String str) return quote(str);
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append(quote(String.valueOf(entry.getKey()))).append(":").append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String source) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}