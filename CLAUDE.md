# CLAUDE.md

本文件为 Claude Code 在本仓库工作时提供指引。

## 项目概述

兰芯数字赛场视频批量下载工具：个人 H5 应用，从第三方平台批量下载篮球比赛视频片段并打包 zip。Java 21 + Spring Boot 3.5.x + 原生 HTML/CSS/JS 前端（单 jar 部署）。

## 常用命令

```bash
./mvnw package              # 构建 + 测试
./mvnw test                 # 仅跑测试
./mvnw spotless:apply       # 格式化（Eclipse JDT，4 空格）
./mvnw spotless:check       # 校验格式
java -jar target/game-video-downloader-0.1.0.jar   # 运行，http://localhost:8080
```

> 用 `./mvnw`（Maven Wrapper 3.9.9），不要用本机 `mvn`（3.6.3 过旧）。

## 架构与约定

- 分层：`controller → service → client（上游代理）/ util`，`download` 包做下载编排。单向依赖、不跨层。
- DTO 一律用 `record`；Service/Config 用 Lombok `@Slf4j` + `@RequiredArgsConstructor`（构造器注入，禁字段注入）。
- 前端静态资源在 `src/main/resources/static/`，改动后需 `./mvnw package` 重新打包生效。
- 命名：片段 `{序号:03d}_{事件}_{号码}_{机位}_{时间戳}.mp4`；zip `{主队}vs{客队}_{日期}.zip`。

## 关键约束（勿改动，否则会坏）

1. **抓 mp4 必须带浏览器 UA + `Referer: https://servicewechat.com/`**，否则 CDN 挂起超时（见 `docs/adr/0003`）。
2. **免费/付费判定**：`path` 含 `/bepaid/` 即付费（付费路径内部也含 `/vslicing/`，须先判 bepaid）。本项目**全量下载、不过滤**（见 `docs/adr/0001`）。
3. **Lombok 注解处理器**：JDK 23+ 不再自动发现，靠 `pom.xml` 里的 `annotationProcessorPaths` 配置生效，勿删（删了 `@Slf4j`/`@RequiredArgsConstructor` 会编译失败）。
4. **Spotless 用 Eclipse JDT**（`eclipse-formatter.xml`，4 空格），勿换成 palantir（JDK 25 不兼容）或 Google Java Format（2 空格）。

## 文档索引

- 领域词汇：`CONTEXT.md`
- 上游 API 分析：`docs/api-analysis.md`
- 功能原型：`docs/prototype.md`
- 代码计划：`docs/code-plan.md`
- 开发规范：`docs/coding-standards.md`
- 架构决策：`docs/adr/`
