package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.LawDto;
import com.contractreview.domain.dto.LawRequest;
import com.contractreview.domain.entity.Law;
import com.contractreview.mapper.LawMapper;
import com.contractreview.service.LawService;
import com.contractreview.util.ChunkingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LawServiceImpl implements LawService {

    private final LawMapper lawMapper;
    private final VectorStore vectorStore;

    @Override
    public LawDto getLaw(Long id) {
        Law law = lawMapper.selectById(id);
        if (law == null) {
            throw new BusinessException(404, "法律法规不存在");
        }
        return toDto(law);
    }

    @Override
    public List<LawDto> listLaws(String category, String keyword) {
        LambdaQueryWrapper<Law> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Law::getCategory, category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Law::getTitle, keyword);
        }
        wrapper.orderByDesc(Law::getCreatedAt);
        return lawMapper.selectList(wrapper).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public LawDto createLaw(LawRequest request, Long userId) {
        Law law = new Law();
        law.setTitle(request.getTitle());
        law.setCategory(request.getCategory());
        law.setContent(request.getContent());
        law.setStatus(1);
        law.setCreatedBy(userId);
        lawMapper.insert(law);

        indexLawToChroma(law);
        return toDto(law);
    }

    @Override
    public void updateLaw(Long id, LawRequest request) {
        Law law = lawMapper.selectById(id);
        if (law == null) {
            throw new BusinessException(404, "法律法规不存在");
        }
        law.setTitle(request.getTitle());
        law.setCategory(request.getCategory());
        law.setContent(request.getContent());
        lawMapper.updateById(law);

        removeLawFromChroma(law.getId());
        indexLawToChroma(law);
    }

    @Override
    public void deleteLaw(Long id) {
        Law law = lawMapper.selectById(id);
        if (law == null) {
            throw new BusinessException(404, "法律法规不存在");
        }
        removeLawFromChroma(law.getId());
        lawMapper.deleteById(id);
    }

    @Override
    public void toggleLaw(Long id) {
        Law law = lawMapper.selectById(id);
        if (law == null) {
            throw new BusinessException(404, "法律法规不存在");
        }
        law.setStatus(law.getStatus() == 1 ? 0 : 1);
        lawMapper.updateById(law);
    }

    @Override
    public void reindexLaw(Long id) {
        Law law = lawMapper.selectById(id);
        if (law == null) {
            throw new BusinessException(404, "法律法规不存在");
        }
        removeLawFromChroma(law.getId());
        indexLawToChroma(law);
    }

    private void indexLawToChroma(Law law) {
        try {
            List<String> chunks = ChunkingUtil.chunkByClause(law.getContent());
            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("lawName", law.getTitle());
                metadata.put("lawId", law.getId());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());
                docs.add(new Document(chunks.get(i), metadata));
            }
            vectorStore.add(docs);
            log.info("Indexed law '{}' ({} chunks) into Chroma", law.getTitle(), docs.size());
        } catch (Exception e) {
            log.warn("Failed to index law '{}' into Chroma: {}", law.getTitle(), e.getMessage());
        }
    }

    private void removeLawFromChroma(Long lawId) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.query("test").withTopK(1000));
            List<Document> toDelete = results.stream()
                    .filter(doc -> {
                        Object id = doc.getMetadata().get("lawId");
                        return id != null && id.toString().equals(String.valueOf(lawId));
                    })
                    .collect(Collectors.toList());
            if (!toDelete.isEmpty()) {
                vectorStore.delete(toDelete.stream().map(Document::getId).collect(Collectors.toList()));
                log.info("Removed {} chunks for law {} from Chroma", toDelete.size(), lawId);
            }
        } catch (Exception e) {
            log.warn("Failed to remove law {} from Chroma: {}", lawId, e.getMessage());
        }
    }

    private LawDto toDto(Law law) {
        return new LawDto(
                law.getId(),
                law.getTitle(),
                law.getCategory(),
                law.getContent(),
                law.getStatus() != null && law.getStatus() == 1,
                law.getCreatedAt() != null ? law.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null,
                law.getUpdatedAt() != null ? law.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
        );
    }
}
