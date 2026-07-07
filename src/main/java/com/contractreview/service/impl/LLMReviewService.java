package com.contractreview.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

    private final ChatClient chatClient;

    private static final String REVIEW_PROMPT = """
            你是一位专业的合同审查律师。请审查以下合同文本，识别其中的风险条款。
            
            请以 JSON 格式输出审查结果，格式如下：
            {
              "summary": "合同审查总结",
              "riskCount": { "high": 0, "medium": 0, "low": 0 },
              "risks": [
                {
                  "clauseIndex": 1,
                  "clauseContent": "条款原文",
                  "riskLevel": "HIGH/MEDIUM/LOW",
                  "riskType": "违约金/免责/管辖/其他",
                  "description": "风险描述",
                  "suggestion": "修改建议",
                  "relatedLaws": ["相关法条"]
                }
              ]
            }
            
            风险等级定义：
            - HIGH：条款明显违法或严重损害用户核心权益
            - MEDIUM：条款可能存在不公平，需进一步协商
            - LOW：条款表述不规范但风险可控
            
            合同文本：
            %s
            """;

    public String reviewContract(String text) {
        log.info("Calling LLM for contract review, text length: {}", text.length());
        String prompt = String.format(REVIEW_PROMPT, text);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("LLM review completed, response length: {}", response != null ? response.length() : 0);
        return response;
    }
}
