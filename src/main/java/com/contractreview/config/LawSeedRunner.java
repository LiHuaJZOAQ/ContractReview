package com.contractreview.config;

import com.contractreview.util.ChunkingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawSeedRunner implements ApplicationRunner {

    private final VectorStore vectorStore;

    @Value("${contract.review.law.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Law seeding is disabled");
            return;
        }

        try {
            List<Document> existing = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.query("test").withTopK(1));
            if (!existing.isEmpty()) {
                log.info("Chroma already has data, skipping law seeding");
                return;
            }
        } catch (Exception e) {
            log.warn("Chroma not available yet, will retry on next startup: {}", e.getMessage());
            return;
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:laws/*.txt");

            if (resources.length == 0) {
                log.warn("No law files found in classpath:laws/");
                return;
            }

            List<Document> allDocs = new ArrayList<>();
            for (Resource resource : resources) {
                String lawName = resource.getFilename();
                log.info("Loading law: {}", lawName);

                String content;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }

                List<String> chunks = ChunkingUtil.chunkByClause(content);
                for (int i = 0; i < chunks.size(); i++) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("lawName", lawName);
                    metadata.put("chunkIndex", i);
                    metadata.put("totalChunks", chunks.size());
                    allDocs.add(new Document(chunks.get(i), metadata));
                }
                log.info("Chunked {} into {} parts", lawName, chunks.size());
            }

            vectorStore.add(allDocs);
            log.info("Successfully seeded {} law chunks into Chroma", allDocs.size());
        } catch (Exception e) {
            log.error("Failed to seed laws into Chroma: {}", e.getMessage());
        }
    }
}
