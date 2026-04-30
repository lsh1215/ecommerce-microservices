package com.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Multi-thread {@link TaskScheduler} so that {@code @Scheduled} methods
 * with disjoint concerns (e.g. {@code OutboxPollingPublisher#publishPendingEvents}
 * doing Kafka I/O and {@code OutboxMetrics#refreshPendingCount} doing a DB
 * count query) don't serialize through Spring's default single-thread pool.
 *
 * <p>Why this matters: under a Kafka outage the publish loop's send call blocks
 * up to {@code 5s × 100 events = 500s} in the worst case while retries elapse.
 * If the metric refresh shares the same thread it never fires, which freezes
 * panel #1 (PENDING depth) during the very incident the panel is meant to
 * highlight. A 2-thread pool keeps refresh ticking on a separate worker so the
 * gauge keeps flowing even when the publish thread is blocked.
 *
 * <p>Pool size of 2 is enough today (only two distinct {@code @Scheduled}
 * tasks). Bump to {@code Math.max(2, Runtime.getRuntime().availableProcessors())}
 * if a third concern is added.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}
