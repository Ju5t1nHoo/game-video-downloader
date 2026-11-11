# 多阶段构建：build 阶段用 JDK 打出 jar，runtime 阶段只留 JRE

# ---- build 阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom，预热依赖层（源码改动不触发重新下载依赖）
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# 再复制源码并打包（含 7 个单测）
COPY src/ src/
RUN mvn -B package

# ---- runtime 阶段 ----
FROM eclipse-temurin:21-jre-jammy

# 非 root 用户运行
RUN useradd --create-home --uid 10001 appuser
WORKDIR /app

COPY --from=builder /build/target/game-video-downloader-0.1.0.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
