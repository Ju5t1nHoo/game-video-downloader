# 代码编写计划

> 版本 v1 · 2026-08-31
> 上游需求见 `docs/prototype.md`；领域词汇 `CONTEXT.md`；关键决策 `docs/adr/`；编码规范见 `docs/coding-standards.md`。

## 1. 技术选型（已定）

| 项 | 选择 | 说明 |
|----|------|------|
| 后端框架 | Spring Boot 3.5.x | 起步器仅 `spring-boot-starter-web` |
| Java | 目标 21 LTS（`--release 21`），本机 JDK 25 编译/运行 | |
| 构建 | Maven | 本机 3.6.3；若与 JDK 25 冲突则换 Maven Wrapper 3.9.x |
| 前端 | 原生 HTML/CSS/JS | 静态资源放 `src/main/resources/static/`，单 jar 部署 |
| HTTP 客户端 | `java.net.http.HttpClient`（内置，零依赖） | 同时用于上游 API 代理 + mp4 下载；固定 UA+Referer |
| Zip | `java.util.zip.ZipOutputStream`（内置） | 从临时文件顺序流式写入响应 |
| 并发 | `ExecutorService`（固定 4 线程，可配） | 片段并发下载 |
| 进度 | 内存 `ConcurrentHashMap<jobId, Progress>` | 单实例、无持久化（重启即失，个人工具可接受） |
| Lombok | `@Slf4j` + `@RequiredArgsConstructor`（仅 Service/Config） | DTO 用 record，见 `docs/coding-standards.md` |
| 格式化 | Spotless + Eclipse JDT（4 空格，`eclipse-formatter.xml`） | `./mvnw spotless:apply` |
| 测试 | JUnit 5，最小单测 | 见 §8 |

## 2. 项目结构

```
game-video-downloader/
├── pom.xml
├── CONTEXT.md
├── docs/{api-analysis.md, prototype.md, code-plan.md, adr/*.md}
└── src/
    ├── main/
    │   ├── java/com/gamevideodownloader/
    │   │   ├── Application.java
    │   │   ├── config/
    │   │   │   ├── AppProperties.java        # @ConfigurationProperties 绑定 app.*
    │   │   │   └── HttpClientConfig.java     # 单个 HttpClient Bean（UA/Referer/超时）
    │   │   ├── client/
    │   │   │   └── UpstreamClient.java       # 上游 API 代理 + mp4 流式下载
    │   │   ├── dto/
    │   │   │   ├── Court.java  Match.java  Clip.java
    │   │   │   └── request/DownloadRequest.java
    │   │   ├── service/
    │   │   │   ├── CourtService.java         # 城市/场馆
    │   │   │   ├── MatchService.java         # 比赛列表
    │   │   │   ├── ClipService.java          # 拉全 PlayByPlay → 平铺 Clip 列表
    │   │   │   └── DownloadService.java      # 并发下载 + zip + 进度编排
    │   │   ├── download/
    │   │   │   ├── DownloadJob.java          # 单次下载任务状态
    │   │   │   ├── ProgressStore.java        # jobId → Progress
    │   │   │   └── ZipStreamer.java          # 临时文件 → ZipOutputStream
    │   │   ├── controller/
    │   │   │   ├── BrowseController.java     # cities/courts/matches/clips
    │   │   │   ├── DownloadController.java   # download + progress
    │   │   │   └── GlobalExceptionHandler.java
    │   │   └── util/
    │   │       ├── ClipClassifier.java       # 含 /bepaid/ → paid
    │   │       └── FileNamer.java            # 片段名 sanitize + zip 名
    │   └── resources/
    │       ├── application.yml
    │       └── static/{index.html, app.js, style.css}
    └── test/java/com/gamevideodownloader/util/
        ├── ClipClassifierTest.java
        └── FileNamerTest.java
```

## 3. 模块职责

| 组件 | 职责 |
|------|------|
| `UpstreamClient` | 唯一出口：封装对 `qiuxinkaifa.com` 的 form-urlencoded POST（返回 JSON）；以及对 mp4 的流式 GET（下载到 `InputStream`/文件），统一注入浏览器 UA + Referer |
| `ClipService` | 翻页拉全 `PlayByPlay(getvideo=1)`，平铺 `events[].videos[]` 为 `Clip`，标注 `paid` |
| `DownloadService` | 编排：校验 path → 并发下载到临时目录 → 顺序 zip 流式回写 → 清理；全程更新 `ProgressStore` |
| `ProgressStore` | 线程安全的 jobId→Progress 映射 |
| `ZipStreamer` | 给定临时文件列表 + 命名，顺序写入 ZipOutputStream |
| `ClipClassifier` / `FileNamer` | 纯函数，易测 |

## 4. REST 接口实现细节

| REST | 上游调用（form-urlencoded POST） | 返回 |
|------|------|------|
| `GET /api/cities` | `/Api` `op=CityList` | 城市列表 |
| `GET /api/courts?lat&lon&cityid&keyword&size&page` | `/Api` `op=CourtsList`（有 lat/lon 就近排序；有 cityid 按城；有 keyword 按 key） | 场馆列表 |
| `GET /api/courts/{courtid}/matches?size&page` | `/Api/Activity` `op=List5v5` | 比赛列表 |
| `GET /api/matches/{actid}/clips` | `/Api/Activity` `op=PlayByPlay` `getvideo=1`，翻页至 pager 耗尽（上限 100 页/5000 事件） | 平铺 Clip 列表 |
| `POST /api/download` | mp4 直链 GET（浏览器 UA+Referer） | 流式 zip |
| `GET /api/download/{jobId}/progress` | — | 进度 JSON |

**下载流程**（`POST /api/download`）：

1. 校验：每个 clip 的 `path` 主机必须为白名单 `qxtechdata.qiuxinkaifa.com`，`name` 经 `FileNamer` sanitize。
2. 阶段 A（并发）：全部 clip 提交到 4 线程池 → 各自 GET mp4 流式写 `{temp}/{jobId}/{index}.tmp`，累计字节；非 2xx/超时/空 body → 记 `skipped{name,reason}`。
3. 阶段 B（顺序 zip）：按序遍历 clip，对已成功下载的临时文件 `ZipStreamer` 写入 `ZipOutputStream`（直连 `HttpServletResponse` 输出流，zip 不落盘）。
4. 收尾：关 zip、删 `{temp}/{jobId}/`；若客户端中断，同样清理。
5. 进度：阶段 A 更新 `{phase:"download", downloaded, total, bytes, skipped}`；阶段 B 更新 `{phase:"zip", done, success, skipped}`。

## 5. 关键类 / 方法要点

- `UpstreamClient.postForm(path, Map<String,String>)` → `JsonNode`：拼 `op=...&k=v`，POST，读 JSON。
- `UpstreamClient.streamMp4(String url, OutputStream out)`：GET + UA/Referer，`BodyHandlers.ofInputStream()` 边读边写、按块计字节。
- `ClipClassifier.isPaid(path)`：`path.contains("/bepaid/")`。
- `FileNamer.clipName(idx, clip)`：`%03d_{jieguo}_{camname去空格括号}_{keytime去-:}` + `.mp4`；`FileNamer.zipName(match)`：`{主队}vs{客队}_{日期}.zip`。
- `ProgressStore.get/update`：`ConcurrentHashMap` + 原子计数。

## 6. 配置项（application.yml）

```yaml
app:
  upstream:
    index-url:  https://qiuxinkaifa.com/index.php/Api
    activity-url: https://qiuxinkaifa.com/index.php/Api/Activity
    user-agent: Mozilla/5.0 (iPhone; ... MicroMessenger/8.0)
    referer: https://servicewechat.com/
    video-host: qxtechdata.qiuxinkaifa.com
  download:
    concurrency: 4
    temp-dir: ${java.io.tmpdir}/gvd
    max-pages: 100
    max-events: 5000
    connect-timeout: 10s
    read-timeout: 60s
```

## 7. 全局错误处理

`@ControllerAdvice` 统一返回 `{code, message}` JSON；非法参数 400、上游失败 502、下载中断仅记录日志。

## 8. 单元测试清单（最小）

| 类 | 用例 |
|----|------|
| `ClipClassifierTest` | 免费（仅 `/vslicing/`）→ false；付费（含 `/bepaid/`，即便内嵌 `/vslicing/`）→ true |
| `FileNamerTest` | camname「侧位（下）」→「侧位下」；keytime「2026-08-27 20:04:35」→「20260827-200435」；序号 3 位补零 |

## 9. 实现顺序

1. `pom.xml` + `Application` + `application.yml` + `AppProperties`/`HttpClientConfig`（骨架可启动）
2. `ClipClassifier` + `FileNamer`（纯函数，先写 + 先测）
3. `dto` + `UpstreamClient`（API 代理，curl 冒烟验证 CityList/List5v5/PlayByPlay）
4. `CourtService`/`MatchService`/`ClipService`
5. `DownloadJob`/`ProgressStore`/`ZipStreamer`/`DownloadService`
6. `BrowseController`/`DownloadController`/`GlobalExceptionHandler`
7. 前端 `static/{index.html, app.js, style.css}`
8. 单测补齐（§8）
9. `mvn package` + 真实 API 冒烟（选一场比赛 → 下载 zip 验证）

## 10. 假设与边界

- 单实例、单用户；进度存内存，重启即失。
- 第三方 API 响应信封 `{rows:[...], pager:{...}}` 或直接数组/对象，客户端按实际情况容错解析。
- mp4 白名单主机硬编码于配置，防止下载端点被当开放代理（SSRF）。
