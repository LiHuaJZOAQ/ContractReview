package com.contractreview.security;

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
}
