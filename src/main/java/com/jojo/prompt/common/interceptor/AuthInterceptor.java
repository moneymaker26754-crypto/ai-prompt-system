package com.jojo.prompt.common.interceptor;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.JwtUtil;
import com.jojo.prompt.common.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (isPublicEndpoint(request.getMethod(), request.getRequestURI())) {
            trySetUserContext(token);
            return true;
        }

        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "not logged in, please log in first");
        }
        return authenticate(token);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    //精确白名单
    private boolean isPublicEndpoint(String method, String uri) {
        if (!HttpMethod.GET.matches(method)) {
            return false;
        }

        if ("/api/prompts/page".equals(uri)
                ||"/api/prompts/hot".equals(uri)
                ||"api/search/hot".equals(uri)
                || "/api/categories/list".equals(uri)
                || "/api/categories/list-with-count".equals(uri)) {
            return true;
        }
        return isPromptDetail(uri) || isCategoryDetail(uri);
    }

    private boolean isPromptDetail(String uri) {
        return uri.matches("^/api/prompts/\\d+$");
    }

    private boolean isCategoryDetail(String uri) {
        return uri.matches("^/api/categories/\\d+$");
    }

    private void trySetUserContext(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            authenticate(token);
        } catch (BusinessException e) {
            log.debug("ignore invalid token on public endpoint: {}", e.getMessage());
        }
    }

    private boolean authenticate(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Long userId = jwtUtil.getUserId(token);
            UserContext.setUserId(userId);
            log.debug("auth success: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("token validation failed: {}", e.getMessage());
            throw new BusinessException(401, "the token is invalid or has expired, please log in again");
        }
    }
}
