# AIDoctor 智能医生助手

一个面向家庭健康管理的 Web 应用：支持多家庭成员档案、药品识别与百科、疾病百科（32 种常见病）、医生在线开方（按成员隔离 + G1/G2 三级校验 + 儿童剂量计算）、AI 问诊与用药安全提示，并带一个会定时弹出健康建议的 IP 助手挂件。

## 技术栈

- **后端**：Java（纯 JDK `com.sun.net.httpserver` + JDBC），单文件 `src/Main.java`，零框架依赖
- **数据库**：MySQL（启动自动初始化表结构与种子数据）
- **前端**：原生 HTML / CSS / JavaScript（无构建步骤，静态资源直接托管）
  - 患者端：`index.html`、`app.js`、`app.css`、`assistant-pet.js` 及 `*.html` 页面
  - 医生端：`script.js`、`styles.css` 及 `doctor-*.html` 页面

## 目录结构

```
AIDoctor/
├── src/Main.java            # 后端服务（HTTP API + JDBC）
├── db/                      # SQL 迁移脚本（migrate_v2 / v3 及回滚、校验）
├── index.html               # 首页（九宫格入口）
├── app.js / app.css         # 患者端脚本与样式
├── script.js / styles.css   # 医生端脚本与样式
├── assistant-pet.js         # IP 助手挂件（场景化健康建议气泡）
├── *.html                   # 各功能页面（问诊/药品/疾病/收藏/反馈/医生端等）
├── images/                  # 图片资源（药品、疾病示意图、IP 形象等）
├── libs/                    # 第三方前端库
└── config.properties        # 本地配置（含密钥，不入库，见下方说明）
```

## 环境要求

- JDK 11+
- MySQL 5.7+ / 8.0+
- 端口 `8080`（后端默认监听）

## 快速开始

1. 准备数据库（默认库名 `ai_doctor`）：

   ```sql
   CREATE DATABASE ai_doctor DEFAULT CHARACTER SET utf8mb4;
   ```

2. 复制配置模板并填入本机信息：

   ```bash
   cp config.properties.example config.properties
   # 编辑 config.properties，设置 DB_URL / 账号密码，以及可选 OCR 密钥
   ```

3. 启动后端：

   ```bash
   cd src
   javac Main.java        # 首次编译
   java Main
   ```

4. 浏览器访问 `http://localhost:8080`（首页 `index.html`）。

> 数据库会在启动时自动执行 `db/` 下的迁移并写入种子数据（32 种疾病、常用药品等）。若初始化失败，程序回退到内存默认数据，不影响前端页面浏览。

## 配置说明

配置优先级：**环境变量 > `config.properties` > 内置默认值**。

`config.properties` 支持 `#` / `//` 注释与 `key=value` 格式，**该文件包含数据库密码与第三方密钥，已加入 `.gitignore`，不会上传到仓库**。请自行在本地创建，可参考 `config.properties.example`。

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_URL` | JDBC 连接串（也可用环境变量） | `jdbc:mysql://localhost:3306/ai_doctor?...` |
| `DB_PASSWORD` | 数据库密码（启动脚本从 config.properties 注入） | 空 |
| `DEEPSEEK_API_KEY` / `OPENAI_API_KEY` | AI 问诊大模型密钥（启动脚本从 config.properties 注入为环境变量） | 空（AI 问诊不可用） |
| `BAIDU_OCR_API_KEY` / `BAIDU_OCR_SECRET_KEY` | 百度 OCR 密钥（药品识别，可选） | 空（使用本地 OCR） |
| `TENCENT_OCR_SECRET_ID` / `TENCENT_OCR_SECRET_KEY` | 腾讯云 OCR 密钥（可选） | 空 |
| `TENCENT_OCR_REGION` | 腾讯云 OCR 区域 | `ap-guangzhou` |
| `OCR_ENGINE` | OCR 引擎偏好：`auto` / `baidu` / `tencent` / `local` | `auto` |

## 主要功能

- **多家庭成员管理**：为每位成员维护独立档案，医生开方、用药安全等按成员隔离。
- **药品识别与百科**：上传药盒图片 OCR 识别，检索药品说明书、用法与注意事项。
- **疾病百科**：32 种常见病科普（含对应示意图），支持详情查看。
- **医生在线开方**：医生端开方，按成员隔离；G1/G2 三级合理性校验 + 儿童剂量自动计算。
- **AI 问诊 / 用药安全**：症状自查、AI 咨询、用药冲突与禁忌提示。
- **IP 助手挂件**：随页面场景轮换弹出健康建议气泡，可点击进入问诊。

## 注意事项

- 本项目为演示 / 学习用途，**不构成任何医疗诊断或治疗建议**，请以专业医生意见为准。
- 上传的 `images/` 与 `.jpeg` 等素材仅用于界面展示。
- `.workbuddy/`、`.idea/`、`*.iml`、`*.bak*` 等本地/编辑器/备份文件均已忽略。
