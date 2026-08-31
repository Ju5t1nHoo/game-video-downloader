# 开发规范

> 版本 v1 · 2026-08-31
> 基准：**阿里巴巴 Java 开发手册（嵩山版）** + **Spring Boot 3 官方实践**。二者冲突时优先 Spring 官方实践（见 §1）。

## 1. 基准与冲突裁决

- 基线：阿里巴巴 Java 开发手册（命名/格式/分层/禁魔法值/Boolean 不加 `is`）。
- 叠加：Spring Boot 3 官方实践（record DTO、构造器注入、@ControllerAdvice）。
- **冲突裁决**（本项目明确选择 Spring 侧）：
  1. DTO 用 `record`（不可变），不用阿里传统的可变 POJO + `@Data`。
  2. Service 不拆接口，不用 `XxxService` + `XxxServiceImpl`（单实现，YAGNI）。
  3. 分包用**分层**（controller/service/client/util），不按功能模块（单域小项目）。

## 2. 命名规范

| 对象 | 规范 | 例 |
|------|------|----|
| 类 / 接口 / 枚举 | 大驼峰 UpperCamelCase | `DownloadService`、`ClipClassifier` |
| 方法 / 变量 / 参数 | 小驼峰 lowerCamelCase | `streamMp4`、`clipName` |
| 常量 | 全大写 + 下划线 | `MAX_PAGES` |
| 包 | 全小写、反域名、无下划线/大写 | `com.gamevideodownloader.service` |
| 后缀 | Controller / Service / Client / Util / Test | `BrowseController`、`FileNamer`（工具类用 `Util`，此处 `FileNamer` 语义更贴切） |

- 禁拼音、禁英文拼音混用；禁魔法值（一律提常量）。
- Boolean 字段**不加 `is` 前缀**（如 `private boolean paid;`）。
- 缩略词仅首字母大写：`HttpClient`、`Uuid`、`Xml`、`Url`。

## 3. 格式规范（Spotless 强制）

- 缩进 **4 空格**（禁 Tab）——用 Spotless 的 Eclipse JDT 格式化器（`eclipse-formatter.xml` 配置 `tabulation.char=space`），**不用 Google Java Format**（后者 2 空格，与阿里冲突）。
- 行宽 ≤ 120。
- import 自动排序、去除未使用 import。
- 运算符两侧各 1 空格；方法间空行。

## 4. DTO 与数据载体

- **请求 / 响应 DTO 一律 `record`**：`DownloadRequest`、`ProgressResponse` 等。
- **领域模型 `record`**：`Court`、`Match`、`Clip`。
- **配置类 `record`**：`AppProperties`，`@ConfigurationProperties` + 构造器绑定。
- **Service / 有状态对象用 `class` + Lombok**：`@RequiredArgsConstructor`（构造器注入）+ `@Slf4j`。
- 禁字段注入（`@Autowired` 字段），禁 `@Data`（可变 DTO）。

## 5. 分层与依赖

```
controller  →  service  →  client（上游代理）/ util
                ↓
             download（下载编排，被 service 调用）
```

- 单向依赖，不跨层、不反向。
- Controller 只做参数校验 + 路由 + 返回，不写业务。
- Service 具体类，构造器注入。

## 6. 异常与日志

- `@ControllerAdvice` 全局异常 → 统一 `{code, message}` JSON。
- 自定义业务异常（如 `UpstreamException`）区分「上游失败 502」。
- `@Slf4j` 日志，占位符 `{}` 拼接，禁字符串 `+` 拼接。

## 7. 工具链

| 工具 | 用途 |
|------|------|
| Maven + `maven-compiler-plugin --release 21` | 构建、Java 21 目标 |
| Spotless（Eclipse JDT 格式化器，`eclipse-formatter.xml`） | `./mvnw spotless:apply` / `spotless:check` 自动格式化（4 空格，兼容 JDK 25） |
| JUnit 5 | 最小单测（ClipClassifier / FileNamer） |

## 8. 前端规范（原生 JS）

- `const` / `let`，禁 `var`；驼峰命名；一个文件一个模块职责。
- 缩进 2 空格（JS 社区惯例）；语义化 id / class。

## 参考资料

- 阿里巴巴 Java 开发手册解析：[CSDN 权威解析](https://blog.csdn.net/weixin_42610010/article/details/152459467)、[阿里手册 15 条强制规约](https://cloud.tencent.cn/developer/article/2722737)
- Spring Boot 3 结构/规范：[六边形架构包结构指南](https://m.php.cn/faq/2608795.html)、[java-coding-standards skill](https://tessl.io/registry/tdg-personal/java-coding-standards/0.1.0/files/SKILL.md)
