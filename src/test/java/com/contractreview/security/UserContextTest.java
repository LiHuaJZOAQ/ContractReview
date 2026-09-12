package com.contractreview.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("InheritableThreadLocal: 通过 new Thread 创建的子线程继承父线程的 userId")
    void testInheritableThreadLocal() throws InterruptedException {
        UserContext.setUserId(99L);

        AtomicReference<Long> childValue = new AtomicReference<>();
        Thread child = new Thread(() -> childValue.set(UserContext.getUserId()));
        child.start();
        child.join();

        assertEquals(99L, childValue.get());
    }

    @Test
    @DisplayName("InheritableThreadLocal: 父线程 clear 不影响已创建的子线程快照")
    void testParentClearDoesNotAffectChildSnapshot() throws InterruptedException {
        UserContext.setUserId(77L);

        AtomicReference<Long> childValue = new AtomicReference<>();
        Thread child = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            childValue.set(UserContext.getUserId());
        });
        child.start();
        Thread.sleep(10);
        UserContext.clear();
        child.join();

        assertEquals(77L, childValue.get());
    }

    @Test
    @DisplayName("wrap: 提交到 commonPool 的 Supplier 仍能拿到 userId")
    void testWrapSupplierForCommonPool() throws ExecutionException, InterruptedException {
        UserContext.setUserId(123L);

        CompletableFuture<Long> future = CompletableFuture.supplyAsync(UserContext.wrap(() -> UserContext.getUserId()));
        assertEquals(123L, future.get());
    }

    @Test
    @DisplayName("wrap: 父线程 clear 后提交到 commonPool 的任务仍能拿到快照")
    void testWrapSupplierSurvivesParentClear() throws ExecutionException, InterruptedException {
        UserContext.setUserId(456L);

        CompletableFuture<Long> future = CompletableFuture.supplyAsync(UserContext.wrap(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return UserContext.getUserId();
        }));

        UserContext.clear();
        assertEquals(456L, future.get());
    }

    @Test
    @DisplayName("wrap: 父线程无 userId 时子线程为 null")
    void testWrapSupplierWithoutSnapshot() throws ExecutionException, InterruptedException {
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(UserContext.wrap(() -> UserContext.getUserId()));
        assertNull(future.get());
    }
}
