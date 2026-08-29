package com.contractreview.service;

import com.contractreview.domain.dto.ClassifyResult;
import com.contractreview.domain.dto.ScanRiskItem;
import com.contractreview.domain.dto.SummarizeResult;

import java.util.List;

public interface AgentService {
    ClassifyResult classifyContract(String fullText);
    List<ScanRiskItem> scanRisks(String chunkContent, List<String> relatedLaws, String strategy);
    SummarizeResult summarizeReport(List<ScanRiskItem> allRisks, String contractType);
}
