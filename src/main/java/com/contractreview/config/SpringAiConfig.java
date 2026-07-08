package com.contractreview.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMinutes(2))
                .setResponseTimeout(Timeout.ofMinutes(2))
                .build();
        var client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build();
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(client));
    }
}
