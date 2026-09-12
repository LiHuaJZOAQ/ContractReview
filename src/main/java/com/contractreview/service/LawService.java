package com.contractreview.service;

import com.contractreview.domain.dto.LawDto;
import com.contractreview.domain.dto.LawRequest;

import java.util.List;

public interface LawService {
    LawDto getLaw(Long id);
    List<LawDto> listLaws(String category, String keyword);
    LawDto createLaw(LawRequest request, Long userId);
    void updateLaw(Long id, LawRequest request);
    void deleteLaw(Long id);
    void toggleLaw(Long id);
    void reindexLaw(Long id);
}
