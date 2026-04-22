package com.jojo.prompt.common.utils;

import jakarta.servlet.http.HttpServletRequest;

//根据用户ID或IP地址构建请求标识符，用于限流等场景
public final class RequestIdentityUtil {
    private RequestIdentityUtil() {
    }

    public static String buildSearchIdentifier(Long userId, HttpServletRequest request) {
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    public static String buildCopyIdentifier(Long userId, HttpServletRequest request) {
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    public static String buildLoginIdentifier(HttpServletRequest request) {
        return "ip:" + getClientIp(request);
    }

    public static String buildLoginFailIdentifier(String username, HttpServletRequest request) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        return normalizedUsername + ":" + buildLoginIdentifier(request);
    }

    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if(xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
