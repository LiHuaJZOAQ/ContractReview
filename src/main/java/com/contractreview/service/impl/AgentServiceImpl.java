package com.contractreview.service.impl;

import com.contractreview.service.AgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String CLASSIFY_PROMPT = """
            你是一位专业的合同审查律师。请分析以下合同文本，输出 JSON 格式的分类结果：
            {
              "contractType": "租赁/劳动/外包/买卖/服务/其他",
              "userStance": "承租方/出租方/劳动者/用人单位/甲方/乙方/其他",
              "reviewStrategy": "简要审查策略说明"
            }
            
            合同文本：
            %s
            """;

    private static final String SCAN_PROMPT = """
            你是一位专业的合同审查律师。请审查以下条款内容，识别其中的风险点。
            
            审查策略：%s
            相关法条：%s
            
            请输出 JSON 数组格式的风险项，每个风险项包含：
            {
              "clauseIndex": 1,
              "clauseContent": "条款原文",
              "riskLevel": "HIGH/MEDIUM/LOW",
              "riskType": "违约金/免责/管辖/其他",
              "description": "风险描述",
              "suggestion": "修改建议",
              "relatedLaws": ["相关法条"]
            }
            
            如果没有风险，返回空数组。
            
            条款内容：
            %s
            """;

    private static final String SUMMARIZE_PROMPT = """
            你是一位专业的合同审查律师。请汇总以下所有风险点，生成审查总结报告。
            
            合同类型：%s
            
            风险点列表：
            %s
            
            请输出 JSON 格式：
            {
              "summary": "审查总结",
              "riskCount": { "high": 整数, "medium": 整数, "low": 整数 },
              "risks": [风险点列表，保持原有格式]
            }
            """;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> classifyContract(String fullText) {
        String text = fullText.length() > 3000 ? fullText.substring(0, 3000) : fullText;
        String prompt = String.format(CLASSIFY_PROMPT, text);
        String response = chatClient.prompt().user(prompt).call().content();
        return (Map<String, String>) (Map) parseMapResult(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMapObjectResult(String response) {
        if (response == null) return Map.of();
        String json = extractJsonObject(response);
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM result: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public List<Map<String, Object>> scanRisks(String chunkContent, List<String> relatedLaws, String strategy) {
        String lawsStr = relatedLaws.isEmpty() ? "无" : String.join("；", relatedLaws);
        String strategyStr = strategy != null ? strategy : "标准审查";
        String prompt = String.format(SCAN_PROMPT, strategyStr, lawsStr, chunkContent);
        String response = chatClient.prompt().user(prompt).call().content();
        String json = extractJsonArray(response);
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse Agent B result: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> summarizeReport(List<Map<String, Object>> allRisks, String contractType) {
        String risksJson;
        try {
            risksJson = objectMapper.writeValueAsString(allRisks);
        } catch (Exception e) {
            risksJson = "[]";
        }
        String prompt = String.format(SUMMARIZE_PROMPT, contractType, risksJson);
        String response = chatClient.prompt().user(prompt).call().content();
        Map<String, Object> result = parseMapObjectResult(response);
        if (result != null && !result.containsKey("risks")) {
            result.put("risks", allRisks);
        }
        if (result != null && !result.containsKey("riskCount")) {
            Map<String, Integer> count = new HashMap<>();
            count.put("high", 0);
            count.put("medium", 0);
            count.put("low", 0);
            result.put("riskCount", count);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMapResult(String response) {
        if (response == null) return Map.of();
        String json = extractJsonObject(response);
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM result: {}", e.getMessage());
            return Map.of();
        }
    }

    private String extractJsonObject(String text) {
        Pattern p = Pattern.compile("\\{[^{}]*+(?:[^{}]*+)*\\}");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group();
        return text;
    }

    private String extractJsonArray(String text) {
        Pattern p = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) return m.group();
        return "[]";
    }
}
