package com.contractreview.config;

import com.contractreview.util.LogTruncator;
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
                            .user("reply OK without any other words")
                            .call()
                            .content();
                    if (response != null && !response.isBlank()) {
                        log.info("LLM API connection test passed, base-url={}", baseUrl);
                    } else {
                        log.warn("LLM API returned empty response, please check model endpoint");
                    }
                } catch (Exception e) {
                    log.error("LLM API connection test failed: {} (url={})", LogTruncator.truncate(e.getMessage(), 200), baseUrl);
                    log.error("Please verify LLM_API_BASE_URL and LLM_API_KEY environment variables");
                    log.error("请检查LLM_API_BASE_URL是否以v1结尾，正确写法不带v1后缀");
                }
            }
        };
    }
}
