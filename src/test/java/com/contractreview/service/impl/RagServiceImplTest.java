package com.contractreview.service.impl;

import com.contractreview.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChatClient chatClient;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        ragService = new RagServiceImpl(vectorStore, chatClient, stringRedisTemplate);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.75);
        ReflectionTestUtils.setField(ragService, "networkCacheTtlDays", 7);
    }

    @Test
    @DisplayName("Redis 缓存命中时直接返回缓存结果")
    void testCacheHit() {
        String chunk = "甲方应按时支付租金";
        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn("《民法典》第703条：租赁合同定义\n《民法典》第722条：租金支付");

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("民法典"));
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("Chroma 检索命中时返回结果并缓存 1d")
    void testChromaHit() {
        String chunk = "违约金为合同总额的30%";
        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("《民法典》第585条：违约金")));

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("585"));
        verify(valueOps).set(eq("rag:laws:" + chunk.hashCode()), contains("585"), eq(1L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Chroma 检索失败（Embedding 404）时降级到 LLM")
    void testChromaFailsFallbackToLLM() {
        String chunk = "试用期6个月";
        String lawResponse = "《劳动合同法》第19条：试用期最长为6个月\n《劳动合同法》第20条：试用期工资";

        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn(null);

        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(contains("试用期"))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(lawResponse);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Connection refused: /v1/embeddings returns 404"));

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("劳动合同法"));
        verify(valueOps).set(eq("rag:laws:" + chunk.hashCode()), contains("劳动合同法"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Chroma 返回空结果时走 LLM 兜底")
    void testChromaEmptyFallbackToLLM() {
        String chunk = "双方发生争议提交仲裁";
        String lawResponse = "《仲裁法》第2条：平等主体合同纠纷可仲裁";

        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn(null);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(contains("仲裁"))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(lawResponse);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("仲裁法"));
    }

    @Test
    @DisplayName("LLM 返回空/无直接相关法条时返回空列表")
    void testLLMReturnsNoLaw() {
        String chunk = "今晚一起吃饭";
        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn(null);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(contains("吃饭"))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("无直接相关法条");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Connection refused: /v1/embeddings returns 404"));

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("超长文本切片到 1000 字符再送 LLM")
    void testLongTextTruncated() {
        String longChunk = "甲".repeat(2000);
        when(valueOps.get("rag:laws:" + longChunk.hashCode())).thenReturn(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Chroma unavailable"));

        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(argThat((String s) -> s.length() < 1500))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("无直接相关法条");

        List<String> result = ragService.retrieveRelevantLaws(longChunk);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("LLM 异常时返回空列表")
    void testLLMException() {
        String chunk = "测试文本";
        when(valueOps.get("rag:laws:" + chunk.hashCode())).thenReturn(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Chroma unavailable"));

        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(contains("测试"))).thenReturn(request);
        when(request.call()).thenThrow(new RuntimeException("API timeout"));

        List<String> result = ragService.retrieveRelevantLaws(chunk);

        assertTrue(result.isEmpty());
    }
}
