package com.knowledgebase.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация асинхронной рассылки уведомлений (Infrastructure Layer).
 *
 * Включает поддержку {@link org.springframework.scheduling.annotation.Async}
 * и создаёт выделенный пул потоков (асинхронную очередь) для отправки писем,
 * чтобы SMTP-операции не блокировали основной поток обработки запросов
 * (требование US4.3.1 / US4.3.2).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    /**
     * Пул потоков для асинхронной отправки email.
     * Используется в {@code @Async("mailTaskExecutor")}.
     *
     * При переполнении очереди задача выполняется в вызывающем потоке
     * (CallerRunsPolicy) — письмо не теряется, лишь временно теряется асинхронность.
     */
    @Bean(name = "mailTaskExecutor")
    public TaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
