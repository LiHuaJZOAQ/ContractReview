package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.service.ReviewStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewStateMachineImplTest {

    @Mock private ReviewTaskMapper taskMapper;

    private ReviewStateMachine stateMachine;
    private ReviewTask task;

    @BeforeEach
    void setUp() {
        stateMachine = new ReviewStateMachineImpl(taskMapper);
        task = new ReviewTask();
        task.setId(1L);
        task.setStatus("PENDING");
        task.setProgress(0);
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING, PARSING, 5",
        "PARSING, RETRIEVING, 20",
        "RETRIEVING, REVIEWING, 40",
        "REVIEWING, SUMMARIZING, 80",
        "SUMMARIZING, SUCCESS, 100",
        "PARSING, FAILED, -1",
        "RETRIEVING, FAILED, -1",
        "REVIEWING, FAILED, -1",
        "SUMMARIZING, FAILED, -1",
        "FAILED, PENDING, 0"
    })
    @DisplayName("所有合法状态转换")
    void testValidTransitions(String from, String to, int expectedProgress) {
        task.setStatus(from);
        task.setProgress(from.equals("FAILED") ? -1 : 50);
        when(taskMapper.selectById(1L)).thenReturn(task);

        stateMachine.transition(1L, from, to);

        assertEquals(to, task.getStatus());
        assertEquals(expectedProgress, task.getProgress());
        verify(taskMapper).updateById(task);
    }

    @Test
    @DisplayName("转换到终端状态时设置 completedAt")
    void testTerminalStateSetsCompletedAt() {
        task.setStatus("SUMMARIZING");
        when(taskMapper.selectById(1L)).thenReturn(task);

        stateMachine.transition(1L, "SUMMARIZING", "SUCCESS");

        assertNotNull(task.getCompletedAt());
    }

    @Test
    @DisplayName("重试时重置 errorMsg 和 completedAt")
    void testRetryResetsErrorAndCompleted() {
        task.setStatus("FAILED");
        task.setErrorMsg("LLM error");
        task.setCompletedAt(LocalDateTime.now());
        task.setProgress(-1);
        when(taskMapper.selectById(1L)).thenReturn(task);

        stateMachine.transition(1L, "FAILED", "PENDING");

        assertEquals(0, task.getProgress());
        assertNull(task.getErrorMsg());
        assertNull(task.getCompletedAt());
    }

    @Test
    @DisplayName("任务不存在时抛异常")
    void testTaskNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(1L, "PENDING", "PARSING"));
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("当前状态不匹配时抛异常")
    void testStatusMismatch() {
        task.setStatus("PARSING");
        when(taskMapper.selectById(1L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(1L, "PENDING", "PARSING"));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非法当前状态抛异常")
    void testInvalidCurrentStatus() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.validateTransition("INVALID", "PENDING"));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非法目标状态抛异常")
    void testInvalidTargetStatus() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.validateTransition("PENDING", "INVALID"));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非法状态转换抛异常（如 PENDING → SUCCESS）")
    void testIllegalTransition() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.validateTransition("PENDING", "SUCCESS"));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("SUCCESS 状态不能转换到任何状态")
    void testSuccessHasNoTransitions() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.validateTransition("SUCCESS", "FAILED"));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }
}
