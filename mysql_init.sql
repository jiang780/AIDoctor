-- ===========================================================
-- 医脉相承 · AI 智能诊疗助手 —— 数据库初始化脚本 (v2)
-- 包含：用户 / 药品(含品牌) / 病种(含分类·病因·症状·关注点·浏览量) / 健康档案 /
--      问诊记录 / 医生 / 接诊 / 反馈
-- 用法：Navicat 中连接本地 MySQL 后运行本脚本；
--      也可直接启动应用，程序会自动建表并补齐缺失字段（推荐）。
-- ===========================================================
CREATE DATABASE IF NOT EXISTS ai_doctor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_doctor;

-- MySQL dump 10.13  Distrib 9.3.0, for Win64 (x86_64)
--
-- Host: localhost    Database: ai_doctor
-- ------------------------------------------------------
-- Server version	9.3.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `chat_records`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `chat_records` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_records`
--

LOCK TABLES `chat_records` WRITE;
/*!40000 ALTER TABLE `chat_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `chat_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `consultations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `consultations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patient_username` varchar(100) NOT NULL,
  `doctor_username` varchar(100) NOT NULL,
  `doctor_name` varchar(100) DEFAULT NULL,
  `specialty` varchar(100) DEFAULT NULL,
  `cond_text` varchar(2000) DEFAULT NULL,
  `status` varchar(20) DEFAULT '待回复',
  `reply` varchar(2000) DEFAULT NULL,
  `patient_read` tinyint DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `replied_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `consultations`
--

LOCK TABLES `consultations` WRITE;
/*!40000 ALTER TABLE `consultations` DISABLE KEYS */;
/*!40000 ALTER TABLE `consultations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `diseases`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `diseases` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `tag` varchar(100) NOT NULL,
  `description` varchar(500) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `category` varchar(100) DEFAULT '常见疾病',
  `causes` varchar(500) DEFAULT NULL,
  `symptoms` varchar(500) DEFAULT NULL,
  `focus` varchar(500) DEFAULT NULL,
  `views` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=138 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `diseases`
--

LOCK TABLES `diseases` WRITE;
/*!40000 ALTER TABLE `diseases` DISABLE KEYS */;
INSERT IGNORE INTO `diseases` VALUES (1,'上呼吸道感染','常见感染','多表现为鼻塞、流涕、咽痛、低热，通常与受凉或病毒感染有关。','2026-11-02 02:56:07','呼吸系统疾病','病毒或细菌感染，受凉、疲劳后抵抗力下降','鼻塞、流涕、咽痛、低热','持续高热超过3天或症状加重需就医',168),(2,'急性扁桃体炎','咽喉疾病','常见咽痛明显、吞咽困难、口臭，部分伴有发热和淋巴结肿大。','2026-11-02 02:56:07','呼吸系统疾病','细菌或病毒感染，受凉、疲劳后抵抗力下降','咽痛、发热、吞咽困难','扁桃体红肿、有脓点',222),(3,'过敏性鼻炎','过敏类','反复打喷嚏、鼻痒、流清鼻涕，通常在季节变化或接触过敏原时加重。','2026-11-02 02:56:07','呼吸系统疾病','花粉、尘螨等过敏原刺激，遗传过敏体质','打喷嚏、鼻痒、清水样鼻涕','季节性反复发作、常伴眼痒',143),(4,'胃炎','消化类','可见上腹隐痛、反酸、嗳气，部分患者伴恶心、食欲下降。','2026-11-02 02:56:07','消化系统疾病','幽门螺杆菌感染、饮食不规律、刺激性食物','上腹隐痛、反酸、嗳气、恶心','空腹或进食后上腹不适需胃镜检查',176),(5,'急性肠胃炎','消化类','典型表现为腹痛、腹泻、恶心、呕吐，需注意补液和饮食管理。','2026-11-02 02:56:07','消化系统疾病','进食不洁食物、细菌或病毒感染','腹痛、腹泻、恶心、呕吐','脱水、发热、血便需及时就医',198),(6,'支气管炎','呼吸类','常见咳嗽、痰多、胸闷，可能由感染、刺激物或过敏诱发。','2026-11-02 02:56:07','呼吸系统疾病','病毒或细菌感染、长期吸烟、空气污染等','咳嗽、咳痰、气喘、胸闷','痰液呈黄绿色、伴发热',55),(7,'流行性感冒','常见感染','由流感病毒引起，起病急，常伴高热、全身酸痛、乏力，传染性较强。','2026-11-02 02:56:07','呼吸系统疾病','流感病毒感染，冬春季高发，传染性强','高热、全身酸痛、乏力、咳嗽','起病急、体温可达39℃以上',205),(8,'哮喘','呼吸类','反复发作的喘息、气促、胸闷或咳嗽，常在夜间或接触过敏原时加重。','2026-11-02 02:56:07','呼吸系统疾病','遗传因素、过敏原刺激、呼吸道感染等','反复发作的喘息、呼吸困难、胸闷、咳嗽','夜间和清晨症状加重，可闻及哮鸣音',54),(9,'胃食管反流病','消化类','胃内容物反流引起烧心、反酸、胸骨后不适，常在餐后或平卧时加重。','2026-11-02 02:56:07','消化系统疾病','食管下括约肌松弛、肥胖、饮食过饱','烧心、反酸、胸骨后不适','餐后或平卧时加重',121),(10,'偏头痛','神经类','反复发作的一侧搏动性头痛，常伴恶心、畏光、畏声。','2026-11-02 02:56:07','神经系统疾病','遗传、内分泌变化、睡眠不足、精神紧张','单侧搏动性头痛、恶心、畏光','反复发作，可伴视觉先兆',89),(11,'高血压','慢病类','以体循环动脉压升高为主要表现，多数起病隐匿，需长期监测和管理。','2026-11-02 02:56:07','心血管疾病','遗传、高盐饮食、肥胖、长期精神紧张','头晕、头痛、颈项发紧','多数起病隐匿，需长期监测管理',260),(12,'2型糖尿病','慢病类','以血糖升高为特征，常见多饮、多食、多尿、体重下降等表现。','2026-11-02 02:56:07','内分泌疾病','胰岛素抵抗、肥胖、遗传、缺乏运动','多饮、多食、多尿、体重下降','空腹血糖≥7.0mmol/L需规范治疗',231),(13,'慢性咽炎','咽喉疾病','咽部异物感、干痒、灼热，晨起刺激性干咳，病程较长。','2026-11-02 02:56:07','呼吸系统疾病','长期用嗓、烟酒刺激、鼻部疾病分泌物刺激','咽部异物感、干痒、灼热','晨起刺激性干咳、病程较长',112),(14,'中耳炎','耳鼻喉类','耳痛、听力下降、耳闷，部分伴有发热或耳流脓。','2026-11-02 02:56:07','耳鼻喉疾病','上呼吸道感染经咽鼓管蔓延、耳部进水','耳痛、听力下降、耳闷','儿童多见，可伴发热或耳流脓',76),(15,'结膜炎','眼科类','眼红、异物感、流泪、分泌物增多，可伴明显痒感。','2026-11-02 02:56:07','眼科疾病','细菌或病毒感染、过敏、异物刺激','眼红、异物感、流泪、分泌物增多','伴明显痒感多为过敏性结膜炎',64),(16,'湿疹','皮肤类','皮肤红斑、丘疹、瘙痒，反复发作，可伴渗出或脱屑。','2026-11-02 02:56:07','皮肤疾病','皮肤屏障受损、过敏体质、环境潮湿','红斑、丘疹、瘙痒、脱屑','反复发作，搔抓后易渗出感染',133),(17,'颈椎病','骨关节类','颈肩部酸痛、僵硬，可伴上肢麻木、头晕等。','2026-11-02 02:56:07','骨关节疾病','长期低头、姿势不良、椎间盘退变','颈肩酸痛、僵硬、上肢麻木','伴头晕或手麻需及时就诊',158),(18,'泌尿系感染','泌尿类','尿频、尿急、尿痛，可伴下腹不适或发热。','2026-11-02 02:56:07','泌尿系统疾病','细菌逆行感染、饮水少、习惯性憋尿','尿频、尿急、尿痛','伴发热腰痛提示上尿路感染',97),(19,'肺炎','呼吸类','发热、咳嗽、咳痰，可伴胸痛、气促，需及时就医。','2026-11-02 02:56:07','呼吸系统疾病','细菌、病毒或支原体感染','发热、咳嗽、咳痰、胸痛','气促、血氧下降需急诊处理',187),(20,'失眠症','神经类','入睡困难、易醒、早醒或睡眠质量差，影响日间状态。','2026-11-02 02:56:07','精神心理疾病','精神压力、作息紊乱、焦虑抑郁情绪','入睡困难、易醒、早醒、日间困倦','持续超过一个月需专业评估',174),(62,'焦虑症','精神心理类','以持续性的过度担忧和紧张不安为主要表现，常伴心悸、坐立不安等躯体症状。','2026-11-02 03:11:23','精神心理疾病','遗传因素、性格因素、应激事件等','过度担忧、紧张不安、心悸','难以控制的担心',237),(63,'强迫症','精神心理类','以反复出现的强迫观念与强迫行为为特征，常因无法控制而明显影响生活。','2026-11-02 03:11:23','精神心理疾病','遗传因素、神经生化异常、心理因素等','反复出现的强迫思维和强迫行为','明知不必要但无法控制',146),(64,'慢性阻塞性肺病','呼吸类','以持续气流受限为特征，常见慢性咳嗽、咳痰和活动后气促，需长期规范管理。','2026-11-02 03:11:23','呼吸系统疾病','长期吸烟、空气污染、职业粉尘等','慢性咳嗽、咳痰、进行性呼吸困难','桶状胸、活动后气促明显',165),(65,'冠心病','慢病类','冠状动脉粥样硬化导致心肌供血不足，常见胸闷、胸痛，需规范抗血小板与调脂治疗。','2026-11-02 03:11:23','心血管疾病','动脉粥样硬化、高血压、糖尿病、吸烟等','心绞痛、胸闷、气短','劳累后发作，休息或含服硝酸甘油缓解',227),(66,'胃溃疡','消化类','胃黏膜缺损超过黏膜肌层，典型表现为规律性上腹痛，需根除幽门螺杆菌并规范抑酸。','2026-11-02 03:11:23','消化系统疾病','幽门螺杆菌感染、长期服用非甾体抗炎药、精神紧张等','上腹疼痛、反酸、嗳气、恶心','餐后痛为主，疼痛有规律性',130),(67,'肠易激综合征','消化类','以腹痛伴排便习惯改变为特征的功能性肠病，需调整饮食与情绪管理。','2026-11-02 03:11:23','消化系统疾病','肠道功能紊乱、精神心理因素、饮食不当等','腹痛、腹胀、排便习惯改变','症状与排便相关，排便后缓解',151),(68,'高脂血症','慢病类','血脂水平异常升高，早期常无症状，长期可加速动脉粥样硬化。','2026-11-02 03:11:23','心血管疾病','高脂高糖饮食、肥胖、缺乏运动、遗传','多无明显症状，体检发现血脂升高','合并高血压糖尿病心血管风险更高',118),(69,'骨质疏松','骨关节类','骨量减少、骨微结构破坏导致骨脆性增加，中老年人群高发。','2026-11-02 03:11:23','骨关节疾病','年龄增长、绝经、钙与维生素D摄入不足','腰背疼痛、身高变矮、易骨折','轻微外力即骨折需骨密度评估',72),(70,'荨麻疹','皮肤类','皮肤黏膜小血管扩张渗出形成风团，常反复发作，需寻找并规避诱因。','2026-11-02 03:11:23','皮肤疾病','食物或药物过敏、感染、冷热刺激','风团、剧烈瘙痒、时起时消','伴呼吸困难或喉头水肿需急诊',105),(71,'痛风','骨关节类','尿酸盐结晶沉积关节引起急性炎症，需控制嘌呤摄入并规范降尿酸。','2026-11-02 03:11:23','骨关节疾病','高嘌呤饮食、饮酒、尿酸排泄减少','关节红肿热痛，多见于足大趾','夜间突发剧痛、易反复发作',141),(72,'缺铁性贫血','慢病类','体内铁储备不足导致血红蛋白合成减少，表现为乏力、头晕与面色苍白。','2026-11-02 03:11:23','血液系统疾病','铁摄入不足、慢性失血、吸收障碍','乏力、头晕、面色苍白、心悸','女性月经量多者高发',88),(73,'抑郁症','精神心理类','以显著而持久的情绪低落为核心表现，可伴兴趣减退、精力下降和睡眠障碍。','2026-11-02 03:11:23','精神心理疾病','遗传、神经递质紊乱、重大生活事件','情绪低落、兴趣减退、睡眠障碍','持续两周以上需专业评估',219);
/*!40000 ALTER TABLE `diseases` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `doctors`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `doctors` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `name` varchar(100) NOT NULL,
  `specialty` varchar(100) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `doctors`
--

LOCK TABLES `doctors` WRITE;
/*!40000 ALTER TABLE `doctors` DISABLE KEYS */;
INSERT IGNORE INTO `doctors` VALUES (1,'doctor1','123456','张医生','全科','2026-11-02 02:56:07'),(2,'doctor2','123456','李医生','呼吸科','2026-11-02 02:56:07'),(3,'doctor3','123456','王医生','消化科','2026-11-02 02:56:07');
/*!40000 ALTER TABLE `doctors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedbacks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `feedbacks` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `type` varchar(50) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `contact` varchar(200) DEFAULT NULL,
  `status` varchar(20) DEFAULT '待处理',
  `reply` varchar(1000) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedbacks`
--

LOCK TABLES `feedbacks` WRITE;
/*!40000 ALTER TABLE `feedbacks` DISABLE KEYS */;
INSERT IGNORE INTO `feedbacks` VALUES (1,'admin','功能建议','希望增加用药提醒功能','15696969153','待处理',NULL,'2026-11-02 03:12:22');
/*!40000 ALTER TABLE `feedbacks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `health_profiles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `health_profiles` (
  `username` varchar(100) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` varchar(20) DEFAULT NULL,
  `blood_type` varchar(20) DEFAULT NULL,
  `height` varchar(20) DEFAULT NULL,
  `weight` varchar(20) DEFAULT NULL,
  `allergies` varchar(500) DEFAULT NULL,
  `chronic` varchar(500) DEFAULT NULL,
  `notes` varchar(500) DEFAULT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `email` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `health_profiles`
--

LOCK TABLES `health_profiles` WRITE;
/*!40000 ALTER TABLE `health_profiles` DISABLE KEYS */;
INSERT IGNORE INTO `health_profiles` VALUES ('admin','系统管理员','男','','','','','','','','15696969153','admin@example.com');
/*!40000 ALTER TABLE `health_profiles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `medicines`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `medicines` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `category` varchar(100) NOT NULL,
  `use_desc` varchar(500) NOT NULL,
  `price` varchar(50) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `brand` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_medicines_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=925 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `medicines`
--

LOCK TABLES `medicines` WRITE;
/*!40000 ALTER TABLE `medicines` DISABLE KEYS */;
INSERT IGNORE INTO `medicines` VALUES (309,'对乙酰氨基酚缓释片','退烧止痛','适用于发热、头痛、关节痛等轻中度疼痛。','¥15.8','2026-11-02 03:00:42',NULL),(310,'布洛芬缓释胶囊','止痛类','用于缓解发热、头痛、牙痛和关节痛。','¥21.7','2026-11-02 03:00:42',NULL),(311,'复方阿司匹林','消炎止痛','适用于轻度到中度疼痛和炎症反应。','¥22.4','2026-11-02 03:00:42',NULL),(312,'布洛芬混悬液（美林）','儿童用药','用于1-12岁儿童退热，按体重给药。','¥28.0','2026-11-02 03:00:42',NULL),(313,'对乙酰氨基酚滴剂','儿童用药','用于婴幼儿退热，需按年龄体重精确给药。','¥25.0','2026-11-02 03:00:42',NULL),(314,'阿司匹林肠溶片','退烧止痛','解热镇痛；小剂量抗血小板需遵医嘱使用。','¥12.0','2026-11-02 03:00:42',NULL),(315,'复方对乙酰氨基酚片','退烧止痛','用于头痛、牙痛、神经痛及感冒发热。','¥13.5','2026-11-02 03:00:42',NULL),(316,'双氯芬酸钠缓释片','消炎止痛','用于各类关节炎及软组织损伤引起的疼痛。','¥19.0','2026-11-02 03:00:42',NULL),(317,'洛索洛芬钠片','消炎止痛','用于骨关节炎、肩周炎、牙痛等疼痛。','¥23.5','2026-11-02 03:00:42',NULL),(318,'塞来昔布胶囊','消炎止痛','用于骨关节炎、类风湿关节炎引起的疼痛。','¥45.0','2026-11-02 03:00:42',NULL),(319,'酚咖片','退烧止痛','用于普通感冒或流感引起的发热、头痛。','¥14.5','2026-11-02 03:00:42',NULL),(320,'连花清瘟胶囊','感冒类','用于风热感冒引起的发热、咽痛、鼻塞等症状。','¥29.9','2026-11-02 03:00:42',NULL),(321,'感冒清热颗粒','感冒类','用于风热感冒引起的发热、咽痛等症状。','¥18.9','2026-11-02 03:00:42',NULL),(322,'复方氨酚烷胺片','感冒类','用于感冒及流感引起的发热、头痛、鼻塞、流涕。','¥15.5','2026-11-02 03:00:42',NULL),(323,'感冒灵颗粒','感冒类','缓解感冒引起的头痛、发热、鼻塞、咽痛。','¥13.9','2026-11-02 03:00:42',NULL),(324,'氨咖黄敏胶囊','感冒类','用于缓解普通感冒及流感引起的发热头痛。','¥9.9','2026-11-02 03:00:42',NULL),(325,'双黄连口服液','感冒类','用于风热感冒引起的发热、咳嗽、咽痛。','¥22.0','2026-11-02 03:00:42',NULL),(326,'板蓝根颗粒','感冒类','清热解毒，用于咽喉肿痛、口咽干燥。','¥12.5','2026-11-02 03:00:42',NULL),(327,'小柴胡颗粒','感冒类','用于外感病邪犯少阳证，寒热往来、食欲不振。','¥16.8','2026-11-02 03:00:42',NULL),(328,'蒲地蓝消炎口服液','感冒类','清热解毒，用于咽炎、扁桃体炎、腮腺炎。','¥32.0','2026-11-02 03:00:42',NULL),(329,'维C银翘片','感冒类','用于流行性感冒引起的发热、头痛、咽痛。','¥8.5','2026-11-02 03:00:42',NULL),(330,'银翘解毒片','感冒类','用于风热感冒发热头痛、咳嗽口干。','¥11.0','2026-11-02 03:00:42',NULL),(331,'荆防颗粒','感冒类','用于风寒感冒引起的头痛身痛、恶寒无汗。','¥14.8','2026-11-02 03:00:42',NULL),(332,'金花清感颗粒','感冒类','用于流感风热犯肺证，发热、咽痛、咳嗽。','¥39.0','2026-11-02 03:00:42',NULL),(333,'阿莫西林分散片','抗感染','用于敏感菌引起的呼吸道、泌尿道感染。','¥18.5','2026-11-02 03:00:42',NULL),(334,'头孢克洛缓释片','抗感染','用于敏感菌引起的呼吸道和中耳感染。','¥24.8','2026-11-02 03:00:42',NULL),(335,'头孢克肟颗粒','抗感染','用于敏感菌引起的呼吸道、泌尿道感染。','¥28.0','2026-11-02 03:00:42',NULL),(336,'头孢氨苄胶囊','抗感染','用于敏感菌引起的皮肤软组织感染。','¥13.8','2026-11-02 03:00:42',NULL),(337,'阿奇霉素分散片','抗感染','用于支原体、衣原体感染及呼吸道感染。','¥21.0','2026-11-02 03:00:42',NULL),(338,'罗红霉素胶囊','抗感染','用于敏感菌引起的呼吸道、耳鼻喉感染。','¥16.5','2026-11-02 03:00:42',NULL),(339,'克拉霉素片','抗感染','幽门螺杆菌根除方案的常用抗生素之一。','¥26.0','2026-11-02 03:00:42',NULL),(340,'左氧氟沙星片','抗感染','用于呼吸道、泌尿道、肠道细菌感染。','¥19.5','2026-11-02 03:00:42',NULL),(341,'甲硝唑片','抗感染','用于厌氧菌感染、滴虫病及口腔感染。','¥6.5','2026-11-02 03:00:42',NULL),(342,'诺氟沙星胶囊','抗感染','用于肠道感染、尿路感染。','¥8.8','2026-11-02 03:00:42',NULL),(343,'复方磺胺甲噁唑片','抗感染','用于敏感菌引起的尿路、呼吸道感染。','¥11.5','2026-11-02 03:00:42',NULL),(344,'奥司他韦胶囊','抗病毒','用于流感病毒感染，可在早期使用控制病情。','¥42.0','2026-11-02 03:00:42',NULL),(345,'利巴韦林颗粒','抗病毒','用于呼吸道合胞病毒引起的病毒性感染。','¥12.0','2026-11-02 03:00:42',NULL),(346,'阿昔洛韦片','抗病毒','用于疱疹病毒感染，如带状疱疹、水痘。','¥15.0','2026-11-02 03:00:42',NULL),(347,'抗病毒口服液','抗病毒','用于风热感冒、流感及病毒性感冒。','¥19.9','2026-11-02 03:00:42',NULL),(348,'蓝芩口服液','抗病毒','清热解毒，用于咽痛、咽干、急性咽炎。','¥36.0','2026-11-02 03:00:42',NULL),(349,'盐酸氨溴索片','止咳类','用于咳嗽、痰多等症状的辅助治疗。','¥19.6','2026-11-02 03:00:42',NULL),(350,'氨溴特罗口服液','止咳类','用于急慢性呼吸道疾病引起的咳嗽、痰多。','¥28.5','2026-11-02 03:00:42',NULL),(351,'复方甘草片','止咳类','用于镇咳祛痰。','¥7.5','2026-11-02 03:00:42',NULL),(352,'右美沙芬缓释片','止咳类','用于感冒、支气管炎等引起的干咳。','¥16.0','2026-11-02 03:00:42',NULL),(353,'急支糖浆','止咳类','用于外感风热所致咳嗽、发热、痰黄。','¥15.9','2026-11-02 03:00:42',NULL),(354,'蜜炼川贝枇杷膏','止咳类','用于伤风咳嗽、痰多咽干。','¥26.5','2026-11-02 03:00:42',NULL),(355,'苏黄止咳胶囊','止咳类','用于感染后咳嗽、咳嗽变异性哮喘。','¥45.0','2026-11-02 03:00:42',NULL),(356,'复方甲氧那明胶囊','止咳类','用于支气管哮喘和咳嗽变异性哮喘。','¥24.0','2026-11-02 03:00:42',NULL),(357,'氨茶碱片','止咳类','用于支气管哮喘、慢性喘息型支气管炎。','¥9.0','2026-11-02 03:00:42',NULL),(358,'沙丁胺醇气雾剂','止咳类','用于缓解哮喘急性发作的喘息症状。','¥22.0','2026-11-02 03:00:42',NULL),(359,'桉柠蒎肠溶软胶囊','止咳类','用于急慢性鼻窦炎、支气管炎的祛痰。','¥32.0','2026-11-02 03:00:42',NULL),(360,'孟鲁司特钠片','过敏类','用于缓解季节性过敏性鼻炎和哮喘相关症状。','¥26.0','2026-11-02 03:00:42',NULL),(361,'氯雷他定片','过敏类','用于过敏性鼻炎、慢性荨麻疹。','¥19.9','2026-11-02 03:00:42',NULL),(362,'西替利嗪片','过敏类','用于季节性和常年性过敏性鼻炎。','¥15.0','2026-11-02 03:00:42',NULL),(363,'马来酸氯苯那敏片','过敏类','用于皮肤黏膜过敏、荨麻疹、药疹。','¥3.5','2026-11-02 03:00:42',NULL),(364,'依巴斯汀片','过敏类','用于过敏性鼻炎、慢性荨麻疹。','¥28.0','2026-11-02 03:00:42',NULL),(365,'地氯雷他定干混悬剂','过敏类','用于儿童过敏性鼻炎、荨麻疹。','¥32.0','2026-11-02 03:00:42',NULL),(366,'糠酸莫米松鼻喷雾剂','过敏类','用于成人及儿童过敏性鼻炎。','¥48.0','2026-11-02 03:00:42',NULL),(367,'布地奈德鼻喷雾剂','过敏类','用于常年性及季节性过敏性鼻炎。','¥55.0','2026-11-02 03:00:42',NULL),(368,'奥美拉唑肠溶胶囊','消化类','用于胃溃疡、十二指肠溃疡、反流性食管炎。','¥18.0','2026-11-02 03:00:42',NULL),(369,'雷贝拉唑钠肠溶片','消化类','用于胃食管反流病、消化性溃疡。','¥32.0','2026-11-02 03:00:42',NULL),(370,'铝碳酸镁咀嚼片','消化类','用于胃痛、胃灼热、反酸、嗳气。','¥27.0','2026-11-02 03:00:42',NULL),(371,'多潘立酮片','消化类','用于消化不良、腹胀、恶心呕吐。','¥15.5','2026-11-02 03:00:42',NULL),(372,'枸橼酸莫沙必利片','消化类','用于功能性消化不良伴胃灼热、嗳气。','¥21.0','2026-11-02 03:00:42',NULL),(373,'法莫替丁片','消化类','用于胃酸过多、胃及十二指肠溃疡。','¥12.0','2026-11-02 03:00:42',NULL),(374,'蒙脱石散','消化类','用于成人及儿童急慢性腹泻。','¥16.0','2026-11-02 03:00:42',NULL),(375,'双歧杆菌三联活菌散','肠胃调理','用于调节肠道菌群，改善消化不良。','¥33.5','2026-11-02 03:00:42',NULL),(376,'健胃消食片','肠胃调理','用于脾胃虚弱、消化不良、脘腹胀满。','¥10.0','2026-11-02 03:00:42',NULL),(377,'藿香正气水','肠胃调理','用于外感风寒、内伤湿滞引起的腹泻呕吐。','¥9.9','2026-11-02 03:00:42',NULL),(378,'保和丸','肠胃调理','用于食积停滞、脘腹胀满、嗳腐吞酸。','¥11.5','2026-11-02 03:00:42',NULL),(379,'肠炎宁片','肠胃调理','用于急慢性胃肠炎、腹泻。','¥18.5','2026-11-02 03:00:42',NULL),(380,'复方黄连素片','肠胃调理','用于肠道感染引起的腹泻腹痛。','¥13.0','2026-11-02 03:00:42',NULL),(381,'糖尿病血糖平片','慢病类','用于血糖控制辅助治疗，需遵医嘱使用。','¥47.6','2026-11-02 03:00:42',NULL),(382,'苯磺酸氨氯地平片','慢病类','用于高血压和心绞痛，需遵医嘱长期服用。','¥32.0','2026-11-02 03:00:42',NULL),(383,'硝苯地平控释片','慢病类','用于高血压、稳定型心绞痛。','¥42.0','2026-11-02 03:00:42',NULL),(384,'缬沙坦胶囊','慢病类','用于轻中度高血压的降压治疗。','¥38.0','2026-11-02 03:00:42',NULL),(385,'琥珀酸美托洛尔缓释片','慢病类','用于高血压、心绞痛、心律失常。','¥35.0','2026-11-02 03:00:42',NULL),(386,'盐酸二甲双胍缓释片','慢病类','2型糖尿病的一线降糖药物。','¥18.0','2026-11-02 03:00:42',NULL),(387,'阿卡波糖片','慢病类','用于降低2型糖尿病患者餐后血糖。','¥58.0','2026-11-02 03:00:42',NULL),(388,'格列美脲片','慢病类','用于2型糖尿病的血糖控制。','¥26.0','2026-11-02 03:00:42',NULL),(389,'阿托伐他汀钙片','慢病类','用于高胆固醇血症，预防心血管事件。','¥35.0','2026-11-02 03:00:42',NULL),(390,'非诺贝特胶囊','慢病类','用于高甘油三酯血症。','¥22.0','2026-11-02 03:00:42',NULL),(391,'复方丹参滴丸','慢病类','用于冠心病心绞痛、胸闷气短。','¥29.0','2026-11-02 03:00:42',NULL),(392,'硝酸甘油片','慢病类','用于心绞痛急性发作，需舌下含服。','¥9.5','2026-11-02 03:00:42',NULL),(393,'稳心颗粒','慢病类','用于心悸、气短、早搏。','¥32.0','2026-11-02 03:00:42',NULL),(394,'龙胆泻肝丸','中成药','用于肝火上炎引起的口苦、目赤、胸闷等。','¥38.9','2026-11-02 03:00:42',NULL),(395,'六味地黄丸','中成药','用于肾阴亏损、头晕耳鸣、腰膝酸软。','¥22.0','2026-11-02 03:00:42',NULL),(396,'逍遥丸','中成药','用于肝郁脾虚、月经不调、胸胁胀痛。','¥15.0','2026-11-02 03:00:42',NULL),(397,'归脾丸','中成药','用于心脾两虚、失眠多梦、食欲不振。','¥14.5','2026-11-02 03:00:42',NULL),(398,'补中益气丸','中成药','用于脾胃虚弱、体倦乏力、食少腹胀。','¥13.0','2026-11-02 03:00:42',NULL),(399,'牛黄解毒片','中成药','用于火热内盛、咽喉肿痛、口舌生疮。','¥8.0','2026-11-02 03:00:42',NULL),(400,'清开灵颗粒','中成药','用于病毒性感冒、急性咽炎引起的高热。','¥17.0','2026-11-02 03:00:42',NULL),(401,'云南白药膏','中成药','活血化瘀，用于跌打损伤、风湿疼痛。','¥28.0','2026-11-02 03:00:42',NULL),(402,'正红花油','中成药','用于跌打损伤、风湿骨痛、蚊虫叮咬。','¥7.8','2026-11-02 03:00:42',NULL),(403,'风油精','中成药','用于伤风感冒引起的头痛头晕、蚊虫叮咬。','¥5.5','2026-11-02 03:00:42',NULL),(404,'维生素C泡腾片','营养补充','适用于日常营养补充和增强抵抗力。','¥17.2','2026-11-02 03:00:42',NULL),(405,'复合维生素B片','营养补充','用于B族维生素缺乏引起的营养不良。','¥9.5','2026-11-02 03:00:42',NULL),(406,'维生素E软胶囊','营养补充','用于心脑血管疾病的辅助治疗及抗氧化。','¥13.0','2026-11-02 03:00:42',NULL),(407,'维生素AD滴剂','营养补充','用于维生素A、D缺乏，预防佝偻病。','¥21.0','2026-11-02 03:00:42',NULL),(408,'碳酸钙D3片','营养补充','用于成人及老年人补钙、预防骨质疏松。','¥36.0','2026-11-02 03:00:42',NULL),(409,'葡萄糖酸锌口服液','营养补充','用于缺锌引起的食欲不振、发育迟缓。','¥19.0','2026-11-02 03:00:42',NULL),(410,'多维元素片','营养补充','用于预防和治疗维生素与矿物质缺乏。','¥38.0','2026-11-02 03:00:42',NULL),(411,'琥珀酸亚铁片','营养补充','用于缺铁性贫血的预防和治疗。','¥25.0','2026-11-02 03:00:42',NULL),(412,'莫匹罗星软膏','皮肤外用','用于脓疱疮、毛囊炎等细菌性皮肤感染。','¥22.0','2026-11-02 03:00:42',NULL),(413,'红霉素软膏','皮肤外用','用于脓疱疮、小面积烧烫伤、寻常痤疮。','¥4.5','2026-11-02 03:00:42',NULL),(414,'阿达帕林凝胶','皮肤外用','用于寻常痤疮的粉刺、丘疹治疗。','¥26.0','2026-11-02 03:00:42',NULL),(415,'氢化可的松乳膏','皮肤外用','用于过敏性皮炎、湿疹、神经性皮炎。','¥8.5','2026-11-02 03:00:42',NULL),(416,'炉甘石洗剂','皮肤外用','用于痱子、荨麻疹等急性瘙痒性皮肤病。','¥7.0','2026-11-02 03:00:42',NULL),(417,'特比萘芬乳膏','皮肤外用','用于手足癣、体癣、股癣等真菌感染。','¥16.0','2026-11-02 03:00:42',NULL),(418,'酮康唑乳膏','皮肤外用','用于真菌感染引起的皮肤癣病。','¥13.5','2026-11-02 03:00:42',NULL),(419,'曲安奈德益康唑乳膏','皮肤外用','用于皮炎湿疹伴发真菌感染。','¥15.0','2026-11-02 03:00:42',NULL),(420,'创可贴','皮肤外用','用于小创口、擦伤的应急包扎。','¥3.0','2026-11-02 03:00:42',NULL),(421,'碘伏消毒液','皮肤外用','用于皮肤、黏膜小伤口的消毒处理。','¥6.5','2026-11-02 03:00:42',NULL),(422,'氧氟沙星滴眼液','五官科','用于结膜炎、角膜炎等细菌性眼部感染。','¥12.0','2026-11-02 03:00:42',NULL),(423,'玻璃酸钠滴眼液','五官科','用于干眼症，缓解眼睛干涩疲劳。','¥28.0','2026-11-02 03:00:42',NULL),(424,'左氧氟沙星滴眼液','五官科','用于睑缘炎、结膜炎等外眼部感染。','¥15.0','2026-11-02 03:00:42',NULL),(425,'复方薄荷脑滴鼻液','五官科','用于干燥性鼻炎、鼻出血的湿润保护。','¥8.0','2026-11-02 03:00:42',NULL),(426,'西瓜霜含片','五官科','用于咽喉肿痛、口舌生疮。','¥10.5','2026-11-02 03:00:42',NULL),(427,'金嗓子喉片','五官科','用于急慢性咽喉炎、声音嘶哑。','¥9.0','2026-11-02 03:00:42',NULL),(428,'咽炎片','五官科','用于慢性咽炎引起的咽干、咽痒、干咳。','¥14.0','2026-11-02 03:00:42',NULL),(429,'开喉剑喷雾剂','五官科','用于急慢性咽喉炎、口腔溃疡。','¥28.0','2026-11-02 03:00:42',NULL),(430,'小儿氨酚黄那敏颗粒','儿童用药','用于儿童普通感冒及流感引起的发热头痛。','¥12.0','2026-11-02 03:00:42',NULL),(431,'小儿止咳糖浆','儿童用药','用于儿童感冒引起的咳嗽。','¥9.5','2026-11-02 03:00:42',NULL),(432,'丁桂儿脐贴','儿童用药','用于小儿腹泻、腹痛的辅助治疗。','¥18.0','2026-11-02 03:00:42',NULL),(433,'999感冒灵颗粒','感冒类','解热镇痛，用于感冒引起的头痛、发热、鼻塞、流涕、咽痛。','¥15.5','2026-11-02 03:00:42',NULL),(434,'泰诺（酚麻美敏片）','感冒类','用于缓解感冒及流感引起的发热、头痛、鼻塞、流涕、咳嗽。','¥16.8','2026-11-02 03:00:42',NULL),(435,'新康泰克（美扑伪麻胶囊）','感冒类','用于缓解普通感冒及流行性感冒引起的发热、喷嚏、流涕等症状。','¥18.5','2026-11-02 03:00:42',NULL),(436,'白加黑（氨麻苯美片）','感冒类','日片不嗜睡、夜片助睡眠，用于缓解感冒引起的多种症状。','¥17.0','2026-11-02 03:00:42',NULL),(437,'感康（复方氨酚烷胺片）','感冒类','用于缓解病毒性感冒引起的发热、头痛、鼻塞、咽痛。','¥14.5','2026-11-02 03:00:42',NULL),(438,'快克（复方氨酚烷胺胶囊）','感冒类','用于缓解普通感冒或流感引起的发热、头痛等症状。','¥13.8','2026-11-02 03:00:42',NULL),(439,'泰诺林（对乙酰氨基酚缓释片）','退烧止痛','用于普通感冒或流感引起的发热，缓解轻中度疼痛。','¥14.8','2026-11-02 03:00:42',NULL),(440,'芬必得（布洛芬缓释胶囊）','退烧止痛','用于缓解头痛、关节痛、牙痛等轻中度疼痛及感冒发热。','¥19.8','2026-11-02 03:00:42',NULL),(441,'散利痛（复方对乙酰氨基酚片Ⅱ）','退烧止痛','用于头痛、牙痛、神经痛及发热的对症治疗。','¥16.5','2026-11-02 03:00:42',NULL),(442,'吗丁啉（多潘立酮片）','消化类','用于消化不良、腹胀、嗳气、恶心、呕吐。','¥18.6','2026-11-02 03:00:42',NULL),(443,'斯达舒（维U颠茄铝胶囊）','消化类','用于缓解胃酸过多引起的胃痛、胃灼热感、反酸。','¥22.0','2026-11-02 03:00:42',NULL),(444,'思密达（蒙脱石散）','消化类','用于成人及儿童急慢性腹泻，保护肠道黏膜。','¥17.9','2026-11-02 03:00:42',NULL),(445,'江中健胃消食片','消化类','用于脾胃虚弱所致的食积、不思饮食、脘腹胀满。','¥11.5','2026-11-02 03:00:42',NULL),(446,'开瑞坦（氯雷他定片）','过敏类','用于缓解过敏性鼻炎、慢性荨麻疹等过敏症状。','¥25.9','2026-11-02 03:00:42',NULL),(447,'仙特明（盐酸西替利嗪片）','过敏类','用于季节性或常年性过敏性鼻炎、荨麻疹。','¥23.5','2026-11-02 03:00:42',NULL),(448,'京都念慈菴蜜炼川贝枇杷膏','止咳化痰','润肺止咳，用于燥咳、咽痛、声音嘶哑。','¥28.8','2026-11-02 03:00:42',NULL),(449,'强力枇杷露','止咳化痰','养阴敛肺，止咳祛痰，用于久咳劳嗽。','¥12.8','2026-11-02 03:00:42',NULL),(450,'复方鲜竹沥液','止咳化痰','清热化痰，用于痰热咳嗽、痰黄黏稠。','¥15.6','2026-11-02 03:00:42',NULL),(451,'达克宁（硝酸咪康唑乳膏）','皮肤外用','用于手足癣、体癣、股癣等真菌感染性皮肤病。','¥14.9','2026-11-02 03:00:42',NULL),(452,'999皮炎平（复方醋酸地塞米松乳膏）','皮肤外用','用于局限性瘙痒症、神经性皮炎、接触性皮炎。','¥10.8','2026-11-02 03:00:42',NULL),(453,'钙尔奇D（碳酸钙D3片）','营养补充','用于中老年人、孕产妇等的钙和维生素D补充。','¥36.0','2026-11-02 03:00:42',NULL),(454,'善存（多维元素片）','营养补充','用于预防和治疗因维生素与矿物质缺乏引起的疾病。','¥45.0','2026-11-02 03:00:42',NULL),(455,'21金维他（多维元素片）','营养补充','补充日常多种维生素和矿物质。','¥29.9','2026-11-02 03:00:42',NULL),(456,'络活喜（苯磺酸氨氯地平片）','慢病类','用于治疗高血压及心绞痛，需遵医嘱使用。','¥38.5','2026-11-02 03:00:42',NULL),(457,'拜新同（硝苯地平控释片）','慢病类','用于高血压、冠心病心绞痛的治疗，需遵医嘱使用。','¥33.0','2026-11-02 03:00:42',NULL),(458,'代文（缬沙坦胶囊）','慢病类','用于轻中度原发性高血压，需遵医嘱使用。','¥42.0','2026-11-02 03:00:42',NULL),(459,'格华止（盐酸二甲双胍缓释片）','慢病类','用于2型糖尿病的血糖控制，需遵医嘱使用。','¥28.5','2026-11-02 03:00:42',NULL),(460,'拜唐苹（阿卡波糖片）','慢病类','用于2型糖尿病餐后血糖控制，需遵医嘱使用。','¥56.0','2026-11-02 03:00:42',NULL),(461,'立普妥（阿托伐他汀钙片）','慢病类','用于高胆固醇血症和混合型血脂异常，需遵医嘱使用。','¥52.0','2026-11-02 03:00:42',NULL),(462,'小葵花小儿肺热咳喘口服液','儿童用药','用于儿童感冒、支气管炎等引起的咳嗽、痰黄。','¥24.8','2026-11-02 03:00:42',NULL);
/*!40000 ALTER TABLE `medicines` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT IGNORE INTO `users` VALUES (1,'admin','123456','2026-11-02 02:56:07');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02 11:16:21

-- ===========================================================
-- v3 新增：家庭药箱 / 用药提醒 / 收藏 / 浏览历史
-- （应用启动时会自动建表，此处供手动初始化参考）
-- ===========================================================
CREATE TABLE IF NOT EXISTS cabinet_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  medicine_name VARCHAR(200) NOT NULL,
  spec VARCHAR(100) DEFAULT '',
  quantity INT DEFAULT 1,
  expire_date VARCHAR(20),
  note VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cabinet (username, medicine_name, spec)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medication_plans (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  cabinet_id INT,
  medicine_name VARCHAR(200) NOT NULL,
  dosage VARCHAR(100) DEFAULT '',
  times VARCHAR(200) NOT NULL,
  start_date VARCHAR(20),
  end_date VARCHAR(20),
  active TINYINT DEFAULT 1,
  doctor_username VARCHAR(100) DEFAULT '',
  doctor_name VARCHAR(100) DEFAULT '',
  note VARCHAR(500) DEFAULT '',
  source VARCHAR(20) DEFAULT 'self',
  patient_read TINYINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medication_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  plan_id INT NOT NULL,
  username VARCHAR(100) NOT NULL,
  take_date VARCHAR(20) NOT NULL,
  slot VARCHAR(10) NOT NULL,
  taken_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_med_log (plan_id, take_date, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS favorites (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id VARCHAR(200) NOT NULL,
  title VARCHAR(200) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_favorite (username, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS browse_history (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id VARCHAR(200) NOT NULL,
  title VARCHAR(200) NOT NULL,
  viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_history (username, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 用药安全中心 ----------
CREATE TABLE IF NOT EXISTS drug_rules (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ingredient VARCHAR(100) NOT NULL,
  rule_type VARCHAR(30) NOT NULL,
  target VARCHAR(100) DEFAULT '',
  level VARCHAR(10) DEFAULT 'amber',
  message VARCHAR(400) NOT NULL,
  advice VARCHAR(400) DEFAULT '',
  UNIQUE KEY uk_drug_rule (ingredient, rule_type, target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 规则明细由应用启动时自动写入（见 Main.java 的 DRUG_RULE_SEED）

-- ---------- 化验单解读 ----------
CREATE TABLE IF NOT EXISTS lab_reports (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  raw_text MEDIUMTEXT,
  items_json MEDIUMTEXT,
  result_json MEDIUMTEXT,
  summary VARCHAR(600) DEFAULT '',
  level VARCHAR(10) DEFAULT 'green',
  abnormal_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_lab_user (username, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
