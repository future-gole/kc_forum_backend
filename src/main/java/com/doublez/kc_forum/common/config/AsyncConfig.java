package com.doublez.kc_forum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
@Configuration
@EnableAsync(proxyTargetClass=true)
public class AsyncConfig {
    public static final String DB_PERSISTENCE_EXECUTOR = "dbPersistenceExecutor";
    public static final String REDIS_PERSISTENCE_EXECUTOR = "redisPersistenceExecutor";

    @Bean(DB_PERSISTENCE_EXECUTOR)
    public Executor dbPersistenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("DbPersist-");
        executor.initialize();
        return executor;
    }

    @Bean(REDIS_PERSISTENCE_EXECUTOR)
    public Executor redisPersistenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("RedisPersist-");
        executor.initialize();
        return executor;
    }
}
