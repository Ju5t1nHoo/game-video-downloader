package com.gamevideodownloader.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用配置，绑定 application.yml 的 app.* 前缀。 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Upstream upstream, Download download) {

    /** 上游平台连接配置。 */
    public record Upstream(String indexUrl, String activityUrl, String userAgent, String referer, String videoHost) {
    }

    /** 下载行为配置。 */
    public record Download(int concurrency, String tempDir, int maxPages, int maxEvents, Duration connectTimeout,
            Duration readTimeout) {
    }
}
