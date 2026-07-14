package com.contractreview.service;

import java.util.List;
import java.util.Map;

public interface AgentService {
    Map<String, String> classifyContract(String fullText);
    List<Map<String, Object>> scanRisks(String chunkContent, List<String> relatedLaws, String strategy);
    Map<String, Object> summarizeReport(List<Map<String, Object>> allRisks, String contractType);
}
