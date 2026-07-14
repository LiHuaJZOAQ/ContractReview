package com.contractreview.service.impl;

import com.contractreview.service.AgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock private ChatClient chatClient;
    private ObjectMapper objectMapper;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agentService = new AgentServiceImpl(chatClient, objectMapper);
    }

    private void mockLLMResponse(String responseText) {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(responseText);
    }

    @Test
    @DisplayName("Agent A: 分类合同正常返回")
    void testClassifyContract() {
        String json = "{\"contractType\": \"租赁\", \"userStance\": \"承租方\", \"reviewStrategy\": \"重点审查租金和违约责任\"}";
        mockLLMResponse(json);

        Map<String, String> result = agentService.classifyContract("甲方将房屋出租给乙方...");

        assertEquals("租赁", result.get("contractType"));
        assertEquals("承租方", result.get("userStance"));
    }

    @Test
    @DisplayName("Agent A: 长文本被截断到 3000 字符")
    void testClassifyLongText() {
        String json = "{\"contractType\": \"劳动\", \"userStance\": \"劳动者\", \"reviewStrategy\": \"标准审查\"}";
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(argThat((String s) -> s.length() < 3500))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(json);

        String longText = "a".repeat(5000);
        Map<String, String> result = agentService.classifyContract(longText);

        assertEquals("劳动", result.get("contractType"));
    }

    @Test
    @DisplayName("Agent A: LLM 返回非 JSON 时返回空 Map")
    void testClassifyInvalidResponse() {
        mockLLMResponse("抱歉，我无法分析这份合同");

        Map<String, String> result = agentService.classifyContract("test");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Agent B: 扫描风险正常返回")
    void testScanRisks() throws Exception {
        String json = "[{\"clauseIndex\": 5, \"clauseContent\": \"甲方不承担任何责任\", \"riskLevel\": \"HIGH\", \"riskType\": \"免责\", \"description\": \"不合理免责\", \"suggestion\": \"删除此条款\", \"relatedLaws\": [\"民法典第506条\"]}]";
        mockLLMResponse(json);

        List<Map<String, Object>> result = agentService.scanRisks("甲方不承担任何责任",
                List.of("民法典第506条"), "重点审查免责条款");

        assertEquals(1, result.size());
        assertEquals("HIGH", result.get(0).get("riskLevel"));
        assertEquals("免责", result.get(0).get("riskType"));
    }

    @Test
    @DisplayName("Agent B: 无相关法条时传'无'")
    void testScanRisksNoLaws() {
        String json = "[]";
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(argThat((String s) -> s.contains("相关法条：无")))).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(json);

        List<Map<String, Object>> result = agentService.scanRisks("test", List.of(), "标准审查");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Agent B: LLM 返回非 JSON 数组时返回空列表")
    void testScanRisksInvalidResponse() {
        mockLLMResponse("没有风险");

        List<Map<String, Object>> result = agentService.scanRisks("test", List.of(), "标准审查");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Agent C: 汇总报告正常返回")
    void testSummarizeReport() throws Exception {
        String json = "{\"summary\": \"本合同存在 2 项风险\", \"riskCount\": {\"high\": 1, \"medium\": 1, \"low\": 0}, \"risks\": []}";
        mockLLMResponse(json);

        List<Map<String, Object>> risks = List.of(
                Map.of("clauseIndex", 1, "riskLevel", "HIGH", "description", "风险1"),
                Map.of("clauseIndex", 2, "riskLevel", "MEDIUM", "description", "风险2")
        );
        Map<String, Object> result = agentService.summarizeReport(risks, "租赁");

        assertEquals("本合同存在 2 项风险", result.get("summary"));
        assertEquals(1, ((Map<String, Integer>) result.get("riskCount")).get("high").intValue());
        assertEquals(1, ((Map<String, Integer>) result.get("riskCount")).get("medium").intValue());
    }

    @Test
    @DisplayName("Agent C: 汇总缺少 risks 时用原始数据补全")
    void testSummarizeReportMissingRisks() {
        String json = "{\"summary\": \"无风险\"}";
        mockLLMResponse(json);

        List<Map<String, Object>> risks = List.of(Map.of("clauseIndex", 1, "riskLevel", "LOW", "description", "小问题"));
        Map<String, Object> result = agentService.summarizeReport(risks, "服务");

        assertEquals("无风险", result.get("summary"));
        assertNotNull(result.get("risks"));
        assertEquals(1, ((List) result.get("risks")).size());
    }

    @Test
    @DisplayName("Agent C: 汇总缺少 riskCount 时用 0 填充")
    void testSummarizeReportMissingRiskCount() {
        String json = "{\"summary\": \"无风险\", \"risks\": []}";
        mockLLMResponse(json);

        Map<String, Object> result = agentService.summarizeReport(List.of(), "其他");

        Map<String, Integer> count = (Map<String, Integer>) result.get("riskCount");
        assertEquals(0, count.get("high").intValue());
        assertEquals(0, count.get("medium").intValue());
        assertEquals(0, count.get("low").intValue());
    }

    @Test
    @DisplayName("Agent C: LLM 返回 null 时返回带默认值的 Map")
    void testSummarizeNullResponse() {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(null);

        Map<String, Object> result = agentService.summarizeReport(List.of(), "其他");

        assertFalse(result.isEmpty());
        assertNotNull(result.get("riskCount"));
        assertNotNull(result.get("risks"));
    }
}
