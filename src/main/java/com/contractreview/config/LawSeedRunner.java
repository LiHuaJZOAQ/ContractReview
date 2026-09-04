package com.contractreview.config;

import com.contractreview.domain.entity.Law;
import com.contractreview.mapper.LawMapper;
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
    private final LawMapper lawMapper;

    @Value("${contract.review.law.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Law seeding is disabled");
            return;
        }

        if (lawMapper.selectCount(null) > 0) {
            log.info("law table not empty, skip classpath seeding (DB is source of truth)");
            return;
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:laws/*.txt");

            if (resources.length == 0) {
                log.warn("No law files found in classpath:laws/");
                return;
            }

            for (Resource resource : resources) {
                String lawName = resource.getFilename();
                String content;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }

                Law law = new Law();
                law.setTitle(lawName.replace(".txt", ""));
                law.setCategory("seed");
                law.setContent(content);
                law.setStatus(1);
                law.setCreatedBy(null);
                lawMapper.insert(law);

                seedChunksToChroma(law);
                log.info("Seeded '{}' ({} chars) from classpath", law.getTitle(), content.length());
            }
        } catch (Exception e) {
            log.error("Failed to seed laws", e);
        }
    }

    private void seedChunksToChroma(Law law) {
        try {
            List<String> chunks = ChunkingUtil.chunkByClause(law.getContent());
            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("lawName", law.getTitle());
                metadata.put("lawId", law.getId());
                metadata.put("enabled", true);
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());
                docs.add(new Document(chunks.get(i), metadata));
            }
            vectorStore.add(docs);
        } catch (Exception e) {
            log.warn("Failed to seed chunks for '{}': {}", law.getTitle(), e.getMessage());
        }
    }
}
