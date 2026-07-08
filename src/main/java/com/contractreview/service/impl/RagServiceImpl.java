package com.contractreview.service.impl;

import com.contractreview.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${contract.review.rag.similarity-threshold:0.75}")
    private double similarityThreshold;

    @Value("${contract.review.rag.network-cache-ttl-days:7}")
    private int networkCacheTtlDays;

    @Override
    public List<String> retrieveRelevantLaws(String chunkContent) {
        String cacheKey = "rag:laws:" + chunkContent.hashCode();
        @SuppressWarnings("unchecked")
        List<String> cached = (List<String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> localResults = searchLocal(chunkContent);
        if (!localResults.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, localResults, 1, TimeUnit.DAYS);
            return localResults;
        }

        List<String> networkResults = searchNetwork(chunkContent);
        if (!networkResults.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, networkResults, networkCacheTtlDays, TimeUnit.DAYS);
        }
        return networkResults;
    }

    private List<String> searchLocal(String chunkContent) {
        try {
            SearchRequest request = SearchRequest.query(chunkContent)
                    .withTopK(3)
                    .withSimilarityThreshold((float) similarityThreshold);
            return vectorStore.similaritySearch(request).stream()
                    .map(doc -> doc.getContent())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Chroma search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> searchNetwork(String chunkContent) {
        try {
            String url = "https://flk.npc.gov.cn" + buildSearchQuery(chunkContent);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
            List<String> results = new ArrayList<>();
            for (Element item : doc.select(".law-item, .result-item, .law-text")) {
                String text = item.text().trim();
                if (!text.isEmpty() && text.length() > 20) {
                    results.add(text);
                }
            }
            return results.subList(0, Math.min(3, results.size()));
        } catch (Exception e) {
            log.warn("Network law search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSearchQuery(String chunkContent) {
        String query = chunkContent.length() > 50 ? chunkContent.substring(0, 50) : chunkContent;
        return "/search?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
    }
}
