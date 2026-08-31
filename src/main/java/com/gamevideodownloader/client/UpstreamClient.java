package com.gamevideodownloader.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamevideodownloader.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 上游平台代理：form-urlencoded POST（返回 JSON）+ mp4 流式下载。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpstreamClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    /** 向指定路径 POST 表单，返回解析后的 JSON。 */
    public JsonNode postForm(String url, Map<String, String> params) {
        String body = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(props.download().readTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", props.upstream().userAgent()).POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new UpstreamException("上游返回 HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new UpstreamException("上游响应解析失败", e);
        }
    }

    /** 流式下载 mp4 到输出流，返回写入字节数；非 2xx 视为链接无效。 */
    public long streamMp4(String url, OutputStream out) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(props.download().readTimeout())
                .header("User-Agent", props.upstream().userAgent()).header("Referer", props.upstream().referer()).GET()
                .build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new UpstreamException("视频链接无效：HTTP " + response.statusCode());
        }
        long bytes = 0;
        try (InputStream in = response.body()) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                bytes += n;
            }
            out.flush();
        } catch (IOException e) {
            throw new UpstreamException("视频下载中断", e);
        }
        return bytes;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException e) {
            throw new UpstreamException("上游请求失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("上游请求被中断", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
