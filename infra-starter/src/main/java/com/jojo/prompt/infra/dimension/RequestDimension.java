package com.jojo.prompt.infra.dimension;

/**
 * 请求维度 SPI：限流/幂等组件需要「来源 IP / 当前用户」时通过本接口获取，
 * 使 starter 不依赖 servlet / spring-security。
 *
 * <p>宿主应用（如 ai-prompt-system app）提供实现 Bean（读 HttpServletRequest /
 * SecurityContext）；未提供时组件回退为 "unknown"，功能不受影响。</p>
 */
public interface RequestDimension {

    /** 当前请求来源 IP；无请求上下文时返回 null */
    String currentIp();

    /** 当前登录用户标识；匿名时返回 null */
    String currentUser();
}
