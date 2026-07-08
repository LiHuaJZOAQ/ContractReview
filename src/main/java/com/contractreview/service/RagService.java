package com.contractreview.service;

import java.util.List;
import java.util.Map;

public interface RagService {
    List<String> retrieveRelevantLaws(String chunkContent);
}
