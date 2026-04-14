package com.ecommerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void create_setsEventIdAndNullPublishedAt() {
        OutboxEvent event = OutboxEvent.create(
            "Order", "123", "order.created", "{\"orderId\":123}", "ORDER-001"
        );

        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getAggregateId()).isEqualTo("123");
        assertThat(event.getEventType()).isEqualTo("order.created");
        assertThat(event.getPayload()).isEqualTo("{\"orderId\":123}");
        assertThat(event.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    void markPublished_setsTimestamp() {
        OutboxEvent event = OutboxEvent.create(
            "Order", "123", "order.created", "{}", "ORDER-001"
        );

        event.markPublished();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void incrementRetryCount_incrementsByOne() {
        OutboxEvent event = OutboxEvent.create(
            "Order", "123", "order.created", "{}", "ORDER-001"
        );

        event.incrementRetryCount();
        assertThat(event.getRetryCount()).isEqualTo(1);

        event.incrementRetryCount();
        assertThat(event.getRetryCount()).isEqualTo(2);
    }

    @Test
    void markFailed_setsPublishedAt() {
        OutboxEvent event = OutboxEvent.create(
            "Order", "123", "order.created", "{}", "ORDER-001"
        );

        event.markFailed();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void create_generatesUniqueEventIds() {
        OutboxEvent event1 = OutboxEvent.create("Order", "1", "order.created", "{}", "K1");
        OutboxEvent event2 = OutboxEvent.create("Order", "2", "order.created", "{}", "K2");

        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
    }
}
