package com.contractreview.service.impl;

import com.contractreview.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${contract.review.rag.similarity-threshold:0.75}")
    private double similarityThreshold;

    @Value("${contract.review.rag.network-cache-ttl-days:7}")
    private int networkCacheTtlDays;

    private static final String LAW_RETRIEVAL_PROMPT = """
            你是一位精通中国法律法规的法律专家。根据以下合同条款，列出与之最相关的中国法律法规及具体法条。

            要求：
            1. 只输出与条款直接相关的法律条文
            2. 每条包含法律名称、具体条款号和核心内容
            3. 按相关度从高到低排序
            4. 若无直接相关法条，输出"无直接相关法条"

            输出格式（每行一条）：
            《法律名称》第X条：核心内容摘要

            合同条款：
            %s
            """;

    @Override
    public List<String> retrieveRelevantLaws(String chunkContent) {
        String cacheKey = "rag:laws:" + chunkContent.hashCode();
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Arrays.stream(cached.split("\n")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }

        List<String> results = searchLocal(chunkContent);
        if (!results.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey, String.join("\n", results), 1, TimeUnit.DAYS);
            return results;
        }

        List<String> fallbackResults = llmRetrieve(chunkContent);
        if (!fallbackResults.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey, String.join("\n", fallbackResults),
                    networkCacheTtlDays, TimeUnit.DAYS);
        }
        return fallbackResults;
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
            log.warn("Chroma search failed (falling back to LLM): {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> llmRetrieve(String chunkContent) {
        try {
            String text = chunkContent.length() > 1000 ? chunkContent.substring(0, 1000) : chunkContent;
            String prompt = String.format(LAW_RETRIEVAL_PROMPT, text);
            String response = chatClient.prompt().user(prompt).call().content();
            if (response == null || response.isBlank() || response.contains("无直接相关法条")) {
                return List.of();
            }
            return extractLawLines(response);
        } catch (Exception e) {
            log.warn("LLM law retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> extractLawLines(String text) {
        Pattern p = Pattern.compile("《[^》]+》第[^」：:：]+[：:：]?[^\\n]*");
        Matcher m = p.matcher(text);
        List<String> results = new java.util.ArrayList<>();
        while (m.find()) {
            String line = m.group().trim();
            if (!line.isEmpty() && !results.contains(line)) {
                results.add(line);
            }
        }
        return results.size() > 5 ? results.subList(0, 5) : results;
    }
}
