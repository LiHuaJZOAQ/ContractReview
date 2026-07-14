package com.contractreview.service.impl;

import com.contractreview.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SseServiceImplTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseServiceImpl();
    }

    @Test
    @DisplayName("创建 Emitter 后可通过同一个 taskId 发送进度")
    void testCreateAndSend() {
        SseEmitter emitter = sseService.createEmitter(1L);

        assertNotNull(emitter);
        assertDoesNotThrow(() -> sseService.sendProgress(1L, "PARSING", 5, "解析中"));
    }

    @Test
    @DisplayName("发送 progress 事件")
    void testSendProgress() {
        sseService.createEmitter(1L);
        assertDoesNotThrow(() -> sseService.sendProgress(1L, "PARSING", 5, "解析中"));
    }

    @Test
    @DisplayName("发送 llm_output 事件")
    void testSendLlmOutput() {
        sseService.createEmitter(1L);
        assertDoesNotThrow(() -> sseService.sendLlmOutput(1L, "Agent-A", "合同类型: 租赁"));
    }

    @Test
    @DisplayName("发送 complete 事件")
    void testSendComplete() {
        sseService.createEmitter(1L);
        assertDoesNotThrow(() -> sseService.sendComplete(1L, "100"));
    }

    @Test
    @DisplayName("发送 error 事件")
    void testSendError() {
        sseService.createEmitter(1L);
        assertDoesNotThrow(() -> sseService.sendError(1L, "审查失败"));
    }

    @Test
    @DisplayName("移除 Emitter 并完成")
    void testRemoveEmitter() {
        sseService.createEmitter(1L);
        assertDoesNotThrow(() -> sseService.removeEmitter(1L));
    }

    @Test
    @DisplayName("不存在的 taskId 发送不抛异常")
    void testSendToNonexistentTask() {
        assertDoesNotThrow(() -> sseService.sendProgress(999L, "PARSING", 5, "解析中"));
        assertDoesNotThrow(() -> sseService.removeEmitter(999L));
    }

    @Test
    @DisplayName("Emitter 发送失败时自动移除")
    void testSendFailureRemovesEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        try {
            doThrow(new IOException("Connection lost")).when(emitter)
                    .send(any(SseEmitter.SseEventBuilder.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sseService.createEmitter(1L);

        try {
            var field = SseServiceImpl.class.getDeclaredField("emitters");
            field.setAccessible(true);
            Map<Long, SseEmitter> emitters = (Map<Long, SseEmitter>) field.get(sseService);
            emitters.put(1L, emitter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        sseService.sendProgress(1L, "PARSING", 5, "解析中");

        try {
            var field = SseServiceImpl.class.getDeclaredField("emitters");
            field.setAccessible(true);
            Map<Long, SseEmitter> emitters = (Map<Long, SseEmitter>) field.get(sseService);
            assertNull(emitters.get(1L));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
