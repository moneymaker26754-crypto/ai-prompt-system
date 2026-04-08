package com.jojo.prompt.common.utils;

/**
 * 用户上下文（存储当前登录用户信息）
 * 基于 ThreadLocal，线程隔离
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户 ID
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前用户信息（请求结束时调用）
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}