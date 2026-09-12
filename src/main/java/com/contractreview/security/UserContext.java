package com.contractreview.security;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class UserContext {

    private static final InheritableThreadLocal<Long> currentUserId = new InheritableThreadLocal<>();

    public static void setUserId(Long userId) {
        currentUserId.set(userId);
    }

    public static Long getUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }

    /**
     * 捕获当前线程的 userId 快照,用于传递给其他线程(尤其是 commonPool 这种复用线程的池子)
     */
    public static Long capture() {
        return currentUserId.get();
    }

    /**
     * 在当前线程恢复快照,并执行任务,最后清理
     */
    public static <T> T runWith(Long snapshot, Supplier<T> task) {
        Long previous = currentUserId.get();
        try {
            if (snapshot != null) {
                currentUserId.set(snapshot);
            }
            return task.get();
        } finally {
            if (previous != null) {
                currentUserId.set(previous);
            } else {
                currentUserId.remove();
            }
        }
    }

    /**
     * 将 Runnable 包装为会自动传递 userId 的 Runnable
     * 适用于 CompletableFuture.runAsync / supplyAsync / ThreadPoolExecutor 等线程池场景
     */
    public static Runnable wrap(Runnable task) {
        Long snapshot = capture();
        return () -> runWith(snapshot, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 将 Supplier 包装为会自动传递 userId 的 Supplier
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Long snapshot = capture();
        return () -> runWith(snapshot, task);
    }

    /**
     * 便捷方法:捕获并提交到指定 Executor
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(wrap(task));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(wrap(task));
    }

    public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
        return CompletableFuture.runAsync(wrap(task), executor);
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task, Executor executor) {
        return CompletableFuture.supplyAsync(wrap(task), executor);
    }
}
