package com.contractreview.service.impl;

import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.dto.ScanRiskItem;
import com.contractreview.domain.dto.SummarizeResult;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.mapper.ReviewReportMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.RiskItemMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewResultHandlerTest {

    @Mock private ReviewStateMachine stateMachine;
    @Mock private ReviewTaskMapper taskMapper;
    @Mock private ReviewReportMapper reportMapper;
    @Mock private RiskItemMapper riskItemMapper;
    @Mock private UserMapper userMapper;
    @Mock private SseService sseService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private ObjectMapper objectMapper;
    private ReviewResultHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        handler = new ReviewResultHandler(
                stateMachine, taskMapper, reportMapper, riskItemMapper,
                userMapper, sseService, objectMapper, redisTemplate);
    }

    private SummarizeResult buildResult(String summary, String contractType, String userStance,
                                         List<ScanRiskItem> risks) {
        SummarizeResult r = new SummarizeResult();
        r.setSummary(summary);
        r.setContractType(contractType);
        r.setUserStance(userStance);
        r.setRisks(risks != null ? risks : new ArrayList<>());
        Map<String, Integer> count = new HashMap<>();
        count.put("high", 0);
        count.put("medium", 0);
        count.put("low", 0);
        r.setRiskCount(count);
        return r;
    }

    private ScanRiskItem buildRisk(int clauseIndex, String level) {
        ScanRiskItem item = new ScanRiskItem();
        item.setClauseIndex(clauseIndex);
        item.setClauseContent("条款" + clauseIndex);
        item.setRiskLevel(level);
        item.setRiskType("违约金");
        item.setDescription("风险描述");
        item.setSuggestion("修改建议");
        item.setRelatedLaws(List.of("民法典第585条"));
        return item;
    }

    // ==================== handleSuccess ====================

    @Test
    @DisplayName("handleSuccess: 正常保存审查结果")
    void testHandleSuccess() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setStatus("SUMMARIZING");
        when(taskMapper.selectById(1L)).thenReturn(task);

        SummarizeResult result = buildResult("审查完成", "租赁", "承租方",
                List.of(buildRisk(1, "HIGH")));

        handler.handleSuccess(1L, result);

        verify(stateMachine).transition(1L, TaskStatus.SUMMARIZING, TaskStatus.SUCCESS);
        verify(riskItemMapper).insert(any(RiskItem.class));
        verify(reportMapper).insert(any(ReviewReport.class));
        verify(taskMapper).updateById(argThat((ReviewTask t) -> "租赁".equals(t.getContractType())));
        verify(sseService).sendComplete(1L, "1");
    }

    @Test
    @DisplayName("handleSuccess: 任务不存在时跳过")
    void testHandleSuccessTaskNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(null);

        handler.handleSuccess(1L, buildResult("", null, null, null));

        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    @DisplayName("handleSuccess: 无 risks 时创建空报告")
    void testHandleSuccessNoRisks() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);

        handler.handleSuccess(1L, buildResult("无风险", null, null, List.of()));

        verify(reportMapper).insert(argThat((ReviewReport r) -> r.getRiskCountHigh() == 0));
    }

    @Test
    @DisplayName("风险计数从实际 risks 列表统计")
    void testRiskCountFromActualRisks() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);

        List<ScanRiskItem> risks = List.of(
                buildRisk(1, "HIGH"),
                buildRisk(2, "MEDIUM"),
                buildRisk(3, "LOW"),
                buildRisk(4, "MEDIUM")
        );
        handler.handleSuccess(1L, buildResult("test", null, null, risks));

        verify(reportMapper).insert(argThat((ReviewReport r) ->
                r.getRiskCountHigh() == 1 &&
                r.getRiskCountMedium() == 2 &&
                r.getRiskCountLow() == 1
        ));
    }

    // ==================== handleFailure ====================

    @Test
    @DisplayName("handleFailure: 标记失败并回滚配额")
    void testHandleFailure() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setUserId(10L);
        task.setStatus("REVIEWING");
        when(taskMapper.selectById(1L)).thenReturn(task).thenReturn(task);
        User user = new User();
        user.setId(10L);
        user.setReviewQuota(5);
        when(userMapper.selectById(10L)).thenReturn(user);

        handler.handleFailure(1L, 10L, new ReviewMessage(1L, 10L, 0), new RuntimeException("LLM API error"));

        verify(stateMachine).transition(1L, TaskStatus.REVIEWING, TaskStatus.FAILED);
        verify(taskMapper).updateById(argThat((ReviewTask t) -> t.getErrorMsg() != null));
        verify(redisTemplate.opsForValue()).increment("user:quota:10");
        verify(userMapper).updateById(argThat((User u) -> u.getReviewQuota() == 6));
        verify(sseService).sendError(eq(1L), contains("LLM API error"));
    }

    @Test
    @DisplayName("handleFailure: 任务已成功时跳过")
    void testHandleFailureAlreadySuccess() {
        ReviewTask task = new ReviewTask();
        task.setStatus("SUCCESS");
        when(taskMapper.selectById(1L)).thenReturn(task);

        handler.handleFailure(1L, 1L, new ReviewMessage(1L, 1L, 0), new RuntimeException("error"));

        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    @DisplayName("handleFailure: userId为null时从task获取")
    void testHandleFailureUserIdNull() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setUserId(20L);
        task.setStatus("PARSING");
        when(taskMapper.selectById(1L)).thenReturn(task).thenReturn(task);
        User user = new User();
        user.setId(20L);
        user.setReviewQuota(8);
        when(userMapper.selectById(20L)).thenReturn(user);

        handler.handleFailure(1L, null, new ReviewMessage(1L, null, 0), new RuntimeException("error"));

        verify(stateMachine).transition(1L, TaskStatus.PARSING, TaskStatus.FAILED);
        verify(redisTemplate.opsForValue()).increment("user:quota:20");
    }
}
