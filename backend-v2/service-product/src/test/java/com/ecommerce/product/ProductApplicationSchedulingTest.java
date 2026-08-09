package com.ecommerce.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Regression guard for the G005 dry-run finding: {@code @EnableScheduling} was missing, so the
 * {@code @Scheduled StockReservationSettler.drain()} never ran in async settle mode and the Redis
 * settle queue never drained to the DB.
 *
 * <p>Unit tests call {@code drain()} directly and the settler is {@code @Profile("!test")}, so no
 * behavioral test can observe the scheduler wiring. This cheap annotation-presence assertion fails
 * fast if the annotation is silently dropped again.
 */
class ProductApplicationSchedulingTest {

    @Test
    void productApplicationEnablesScheduling() {
        assertThat(ProductApplication.class.isAnnotationPresent(EnableScheduling.class))
                .as("@EnableScheduling must stay on ProductApplication so the async settle-queue drainer runs")
                .isTrue();
    }
}
