package com.contractreview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ApplicationRunner llmConnectionTester(ChatClient chatClient) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("$")) {
                    log.warn("LLM API key not configured, skipping connection test");
                    return;
                }
                try {
                    String response = chatClient.prompt()
                            .user("请回复OK")
                            .call()
                            .content();
                    if (response != null && !response.isBlank()) {
                        log.info("LLM API connection test passed, base-url={}", baseUrl);
                    } else {
                        log.warn("LLM API returned empty response, please check model endpoint");
                    }
                } catch (Exception e) {
                    log.error("LLM API connection test failed: {} (url={})", e.getMessage(), baseUrl);
                    log.error("Please verify LLM_API_BASE_URL and LLM_API_KEY environment variables");
                }
            }
        };
    }
}
