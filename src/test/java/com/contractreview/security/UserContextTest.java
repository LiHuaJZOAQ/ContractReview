package com.contractreview.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("设置和获取 userId")
    void testSetAndGetUserId() {
        UserContext.setUserId(42L);
        assertEquals(42L, UserContext.getUserId());
    }

    @Test
    @DisplayName("clear 后 userId 为 null")
    void testClear() {
        UserContext.setUserId(1L);
        UserContext.clear();
        assertNull(UserContext.getUserId());
    }

    @Test
    @DisplayName("InheritableThreadLocal: 子线程继承父线程的 userId")
    void testInheritableThreadLocal() throws ExecutionException, InterruptedException {
        UserContext.setUserId(99L);

        CompletableFuture<Long> childValue = CompletableFuture.supplyAsync(UserContext::getUserId);
        assertEquals(99L, childValue.get());
    }

    @Test
    @DisplayName("InheritableThreadLocal: 父线程 clear 不影响已创建的子线程快照")
    void testParentClearDoesNotAffectChildSnapshot() throws ExecutionException, InterruptedException {
        UserContext.setUserId(77L);

        CompletableFuture<Long> childValue = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return UserContext.getUserId();
        });

        UserContext.clear();
        assertEquals(77L, childValue.get());
    }
}
