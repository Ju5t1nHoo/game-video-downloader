# 兰芯数字赛场视频批量下载工具

一个个人使用的 H5 应用：从「兰芯数字赛场」平台批量下载篮球比赛视频片段，打包成 zip。

> ⚠️ **免责声明：本项目仅供学习研究使用，仅用于技术交流。视频内容版权归原平台所有，请勿用于任何商业用途。若有侵犯权益，请及时联系下架。**

---

## 功能特性

- **定位优先 + 城市兜底**：进入自动请求定位展示「附近场馆」，拒绝/失败降级到城市列表；场馆搜索自动带当前定位，按距离从近到远排序。
- **单页逐层下钻**：场馆 → 历史比赛 → 视频片段，面包屑可回退。
- **全量片段平铺列表**：缩略图、球队、事件类型（miss/goal）、机位、时间戳，付费片段带「付费」徽标。
- **勾选与筛选**：全选 / 按主队客队筛选 / 单个勾选；点击行内嵌 `<video>` 预览。
- **并发下载 + 流式打包**：4 路并发抓取到临时目录，顺序打包 zip 流式回写（zip 不落盘，用后即删），实时进度「下载中 X/N 片段 · Y MB」。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21 + Spring Boot 3.5.x + Maven |
| 前端 | 原生 HTML / CSS / JS（静态资源，单 jar 部署） |
| HTTP 客户端 | `java.net.http.HttpClient`（内置，零依赖，伪装浏览器 UA + Referer） |
| 打包 | `java.util.zip.ZipOutputStream`（内置，流式） |
| 进度 | 内存 `ConcurrentHashMap`（单实例，无持久化） |
| 格式化 | Spotless + Eclipse JDT（4 空格，`eclipse-formatter.xml`） |
| 测试 | JUnit 5（7 个单测） |

## 项目结构

```
game-video-downloader/
├── pom.xml                  # Maven 构建（Spring Boot 3.5.16 / Java 21 / Lombok / Spotless）
├── eclipse-formatter.xml    # Spotless 格式化配置（4 空格）
├── mvnw / mvnw.cmd          # Maven Wrapper（固定 3.9.9）
├── CONTEXT.md               # 领域词汇表
├── docs/
│   ├── api-analysis.md      # 上游 API 分析（180+ 接口 + 鉴权分级）
│   ├── prototype.md         # 功能原型
│   ├── code-plan.md         # 代码编写计划
│   ├── coding-standards.md  # 开发规范
│   └── adr/                 # 架构决策记录（0001~0003）
└── src/
    ├── main/java/com/gamevideodownloader/
    │   ├── Application.java
    │   ├── config/          # AppProperties、HttpClientConfig
    │   ├── client/          # UpstreamClient（上游代理）、UpstreamException
    │   ├── dto/             # City/Court/Match/Clip/Page/…（record）
    │   ├── service/         # CourtService/MatchService/ClipService/DownloadService
    │   ├── download/        # DownloadJob/ProgressStore/ZipStreamer
    │   ├── controller/      # BrowseController/DownloadController/GlobalExceptionHandler
    │   ├── exception/       # BadRequestException/NotFoundException
    │   └── util/            # ClipClassifier（免费/付费判定）、FileNamer（命名）
    ├── main/resources/
    │   ├── application.yml
    │   └── static/          # index.html / app.js / style.css
    └── test/java/…/util/    # ClipClassifierTest / FileNamerTest
```

## 快速开始

```bash
# 构建（含测试）
./mvnw package

# 运行
java -jar target/game-video-downloader-0.1.0.jar

# 浏览器打开
open http://localhost:8080
```

格式化（可选）：

```bash
./mvnw spotless:apply   # 4 空格格式化
./mvnw spotless:check   # 校验格式
```

## 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/cities` | 城市列表 |
| GET | `/api/courts?lat=&lon=&cityid=&keyword=` | 场馆列表（带坐标按距离排序） |
| GET | `/api/courts/{courtid}/matches` | 历史比赛列表 |
| GET | `/api/matches/{actid}/clips` | 拉全该场片段（翻页聚合 PlayByPlay） |
| POST | `/api/download` | 下载已选片段，流式回 zip |
| GET | `/api/download/{jobId}/progress` | 轮询进度 |

## 关键设计决策

见 `docs/adr/`：

1. **下载范围含付费片段** —— 平台 mp4 本体公开可读（无 CDN 门槛），「付费」仅在 app 层强制。
2. **并发下载到临时目录，再顺序打包 zip** —— 为并发速度牺牲「流式不落盘」，改为「片段临时落盘、zip 流式输出」。
3. **必须伪装浏览器 UA + Referer** —— CDN 对非浏览器客户端限流，缺这两个头下载必超时。

---

## AI 生成说明

**本项目全部内容由 AI 生成**，人类仅参与需求确认、方案决策与功能验收。从需求分析、原型设计、代码计划、开发规范到编码实现、构建测试，均由 AI（Claude Code）在对话中完成。

### AI 配置

| 项 | 值 |
|----|----|
| 工具 | Claude Code（CLI） |
| 模型 | `deepseek-v4-pro[1m]`（会话环境报告） |
| 上下文窗口 | 约 1500 万 token（会话经多次上下文压缩） |
| 技能插件 | mattpocock-skills：`grill-me`、`grill-with-docs`（内部使用 `grilling` 访谈 + `domain-modeling` 领域建模） |
| 实际调用工具 | Bash（构建/探测/冒烟）、Read、Write、Edit、WebSearch、AskUserQuestion、TaskCreate/TaskUpdate |
| 对话转录 | `~/.claude/projects/-Users-ju5t1n-aiproject-game-video-downloader/350a7abf-5a26-4633-8bf7-0ae825f6beb5.jsonl`（约 2.9 MB） |
| 后台任务目录 | `~/.claude/jobs/350a7abf/` |

### 生成过程

1. **需求分析**（grill-me）：梳理上游 API，枚举 180+ 接口及关系，筛选免鉴权接口 → `docs/api-analysis.md`。
2. **功能原型**（grill-with-docs）：访谈定用户旅程、业务流程，产出原型与 ADR → `docs/prototype.md`、`docs/adr/`。
3. **代码计划**（grill-with-docs）：技术选型、模块划分、实现顺序 → `docs/code-plan.md`。
4. **开发规范**（grill-me）：Web 检索阿里 Java 手册 + Spring Boot 最佳实践 → `docs/coding-standards.md`。
5. **编码实现 + 验收**：按计划实现 29 个 Java 文件 + 前端三件套，`mvn package` 构建通过、7 单测全绿、真实 API 冒烟通过，并修复 Lombok/JDK 兼容与格式化器适配。

---

## 免责声明

本项目仅供学习研究使用，仅用于技术交流，视频内容版权归原平台所有，请勿用于任何商业用途。若有侵犯权益，请及时联系下架。
