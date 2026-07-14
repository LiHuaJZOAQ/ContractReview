package com.contractreview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LawSeedRunner implements ApplicationRunner {

    @Value("${contract.review.law.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Law seeding is disabled");
            return;
        }
        log.info("Chroma vector store is unavailable (Embedding 404). " +
                "Law retrieval is handled by LLM. Skipping Chroma seeding.");
    }
}
