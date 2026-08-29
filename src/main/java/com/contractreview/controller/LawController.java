package com.contractreview.controller;

import com.contractreview.common.R;
import com.contractreview.domain.dto.LawDto;
import com.contractreview.domain.dto.LawRequest;
import com.contractreview.security.UserContext;
import com.contractreview.service.LawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/law")
@RequiredArgsConstructor
@Tag(name = "法律法规", description = "法律法规知识库管理")
public class LawController {

    private final LawService lawService;

    @GetMapping("/{id}")
    @Operation(summary = "获取法律法规详情")
    public R<LawDto> getLaw(@PathVariable Long id) {
        return R.ok(lawService.getLaw(id));
    }

    @GetMapping
    @Operation(summary = "查询法律法规列表", description = "支持按分类和关键词筛选")
    public R<List<LawDto>> listLaws(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return R.ok(lawService.listLaws(category, keyword));
    }

    @PostMapping
    @Operation(summary = "新增法律法规", description = "创建新的法律法规条目并自动索引到向量库")
    public R<LawDto> createLaw(@Valid @RequestBody LawRequest request) {
        Long userId = UserContext.getUserId();
        return R.ok(lawService.createLaw(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新法律法规")
    public R<Void> updateLaw(@PathVariable Long id, @Valid @RequestBody LawRequest request) {
        lawService.updateLaw(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除法律法规")
    public R<Void> deleteLaw(@PathVariable Long id) {
        lawService.deleteLaw(id);
        return R.ok();
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用法律法规")
    public R<Void> toggleLaw(@PathVariable Long id) {
        lawService.toggleLaw(id);
        return R.ok();
    }

    @PostMapping("/{id}/reindex")
    @Operation(summary = "重新索引法律法规", description = "重新将法律法规内容索引到向量库")
    public R<Void> reindexLaw(@PathVariable Long id) {
        lawService.reindexLaw(id);
        return R.ok();
    }
}
