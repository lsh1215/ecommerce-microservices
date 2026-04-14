package com.ecommerce.common.outbox;

import com.ecommerce.common.entity.BaseEntity;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_unpublished", columnList = "publishedAt, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 50)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 100)
    private String partitionKey;

    private LocalDateTime publishedAt;

    private int retryCount;

    @Version
    private Long version;

    public static OutboxEvent create(String aggregateType, String aggregateId,
                                     String eventType, String payload, String partitionKey) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UlidCreator.getMonotonicUlid().toString();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.partitionKey = partitionKey;
        event.retryCount = 0;
        return event;
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void markFailed() {
        this.publishedAt = LocalDateTime.now();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
