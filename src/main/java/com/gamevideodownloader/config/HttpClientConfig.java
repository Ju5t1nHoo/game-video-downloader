package com.gamevideodownloader.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** HTTP 客户端配置：连接超时取自配置，请求级读超时在各调用点设置。 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient(AppProperties props) {
        return HttpClient.newBuilder().connectTimeout(props.download().connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }
}
