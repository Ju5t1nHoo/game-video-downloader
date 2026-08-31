# 功能原型 — 兰芯数字赛场视频批量下载工具

> 版本 v1 · 2026-08-31
> 上游 API 分析见 `docs/api-analysis.md`；领域词汇见 `CONTEXT.md`；关键决策见 `docs/adr/`。

## 1. 概览

个人使用的 H5 应用：从「兰芯数字赛场」平台批量下载篮球比赛视频片段，打包成 zip。**全量下载**——含平台标为付费的片段（其 mp4 本体公开可读，见 ADR-0001）。前端 H5 + 后端 Java（Spring Boot）。

**为什么后端代理一切**：第三方 API 不设 CORS，浏览器跨域无法直连；且视频 CDN 对非浏览器客户端有 UA/Referer 门槛（ADR-0003）。故所有上游调用（列表 + 下载）都经后端代理。

## 2. 用户旅程（单页逐层下钻）

| # | 屏 | 内容 / 交互 |
|---|----|----|
| 1 | **入口** | 进入自动请求定位（浏览器地理授权）。成功 →「附近场馆」；被拒/失败 → 降级「城市列表」。顶部常驻场馆搜索框。 |
| 2 | **场馆列表** | 附近 / 城市 / 搜索三种来源统一呈现，每行：场馆名 + 地址。 |
| 3 | **比赛列表** | 点场馆 → 历史比赛（List5v5），时间倒序，每行：主队 vs 客队 · 比分 · 日期。 |
| 4 | **视频列表** | 点比赛 → 全量片段平铺列表（**含付费，标注「付费」徽标**）。顶部横条：主队 vs 客队 · 比分 · 日期。顶部「全选」+ 按球队/球员筛选。每行：事件类型 + 球员（若有）+ 机位 + 时间戳 + 缩略图(poster) + 勾选框。点行展开内嵌 `<video>` 预览。 |
| 5 | **下载** | 点「下载已选 (N)」→ 后端并发下载 → 实时「已完成 X/N · 已写 Y MB」→ 浏览器收到 zip。 |

## 3. 后端业务流程

```
入口         GetCityByGeo(lat,lon) / CityList / CourtsList(key)      → 城市/场馆
场馆         NearCourt(lat,lon) / CourtsList(lat,lon,cityid)          → courtid
比赛         List5v5(courtid, page, size)                             → actid 列表
视频列表     PlayByPlay(actid, getvideo=1, size, page) 翻页拉全       → 平铺片段(含付费)
下载         POST 已选片段 → 并发下载到临时目录 → 顺序打包 zip 流式回写 → 删临时文件
```

- 视频列表后端**一次拉全**（`size/page` 翻页直至耗尽，上限 100 页/5000 事件），过滤后返回平铺片段列表。
- 下载阶段：**并发**（默认 4 路，可配置）抓片段到临时目录；全部就绪后**顺序**读临时文件写入 `ZipOutputStream` 流式回写（zip 不落盘）；完成后**删除**临时目录。

## 4. REST 接口契约

| 方法 | 路径 | 说明 | 上游代理 |
|------|------|------|----------|
| GET | `/api/cities` | 城市列表 | `CityList` |
| GET | `/api/courts?lat=&lon=&cityid=&keyword=&size=&page=` | 场馆列表 | `NearCourt` / `CourtsList` |
| GET | `/api/courts/{courtid}/matches?size=&page=` | 历史比赛列表 | `List5v5` |
| GET | `/api/matches/{actid}/clips` | 拉全该场片段（平铺） | `PlayByPlay(getvideo=1)` |
| POST | `/api/download` | 下载已选片段，**流式回 zip** | OSS mp4 抓取 |
| GET | `/api/download/{jobId}/progress` | 轮询进度 | — |

**下载接口**（`POST /api/download`）：

- 请求体：`{ "jobId": "<uuid>", "clips": [{"path":"...","name":"..."}] }`
  - `jobId` 由**前端生成**（UUID），保证下载开始前就能轮询进度。
- 响应：`Content-Type: application/zip`，`Content-Disposition: attachment`，body 为流式 zip。
- 进度（`GET /api/download/{jobId}/progress`，前端每 500ms 轮询）：

```json
// 阶段 A — 并发下载到临时目录
{ "phase": "download", "downloaded": 37, "total": 164, "bytes": 74387200, "skipped": [{"name":"..","reason":"timeout"}] }
// 阶段 B — 顺序打包 zip 流式回写
{ "phase": "zip", "done": true, "success": 160, "skipped": [{"name":"..","reason":"404"}] }
```

## 5. 数据模型

**片段 clip**（= 一个 mp4，`PlayByPlay` 返回的 `events[].videos[]` 平铺后）：

| 字段 | 来源 | 说明 |
|------|------|------|
| `notid` | event | 事件 ID |
| `keytime` | event | 事件时间戳（`2026-08-27 20:04:35`） |
| `offensive` | event | 进攻方侧位（middle/far/near…） |
| `jieguo` | event | 结果类型（miss/goal/…） |
| `teamid` / `starid` | event | 球队 / 球员 ID（可选 `getstar=1` 取球员名） |
| `camname` | video | 机位名（如「侧位（下）」） |
| `path` | video | mp4 直链 |
| `poster` | video | OSS 首帧缩略图 jpg |
| `paid` | 派生 | `path` 含 `/bepaid/` 为 true |

## 6. 命名规范

- **zip**：`{主队}vs{客队}_{比赛日期}.zip`（队名来自 `List5v5`；缺失时回退 `{actid}_{日期}.zip`）。
- **片段文件**：`{序号:03d}_{事件}_{机位}_{时间戳}.mp4`
  - 例：`001_miss_侧位下_20260827-200435.mp4`
  - 序号 = zip 内顺序（1 起）；事件 = `jieguo` 原值；机位 = `camname` 去空格/括号；时间戳 = `keytime` 去 `-: `（`20260827-200435`）。

## 7. 边界与错误处理

| 场景 | 处理 |
|------|------|
| 链接失效（非 2xx / 超时 / 空 body） | 跳过该片段，记入 `skipped`（name + reason），不中断整个 zip |
| 并发度 | 默认 4 路，配置项可调 |
| mp4 主机白名单 | 仅允许 `qxtechdata.qiuxinkaifa.com`，防 SSRF/开放代理 |
| 抓取头 | 固定 `User-Agent`(浏览器) + `Referer: https://servicewechat.com/`（ADR-0003） |
| 临时文件 | 打包完成（含失败）即删 `{temp}/{jobId}/` |
| 全部失败 | 返回空 zip 或 4xx + 错误信息，不崩溃 |

## 8. 关键实测事实（2026-08-31）

1. 付费 mp4 匿名可直下（`/bepaid/` 返回 206 + 真实 MP4 字节），CDN 无访问门槛。
2. CDN 有 UA 门槛：默认 UA 的 GET 挂起超时，浏览器 UA + 微信 Referer 秒回。
3. 付费路径含 `/vslicing/`，判定规则必须是「含 `/bepaid/` = 付费，否则免费」。
4. 视频对象带 `poster`（首帧缩略图）与 `camname`（机位名）。
5. 单场实测：221 事件 = 164 免费 + 57 付费片段（`size=221` 可一次拉全，接口未限制单页大小）。
