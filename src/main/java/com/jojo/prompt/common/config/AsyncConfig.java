package com.jojo.prompt.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync//启用异步
//异步配置
public class AsyncConfig {

    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程数
        executor.setCorePoolSize(5);
        //最大线程数
        executor.setMaxPoolSize(10);
        //队列容量
        executor.setQueueCapacity(100);
        //线程名前缀
        executor.setThreadNamePrefix("event-");
        //拒绝策略：当线程池满了之后，调用者线程会自己执行这个任务，而不是抛出异常或者丢弃任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //线程空闲时间
        executor.setKeepAliveSeconds(60);
        //等待所有任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //等待时间
        executor.setAwaitTerminationSeconds(60);
        //初始化线程
        executor.initialize();

        return executor;
    }
}
