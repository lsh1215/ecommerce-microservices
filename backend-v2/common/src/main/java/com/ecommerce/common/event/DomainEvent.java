package com.ecommerce.common.event;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DomainEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;

    protected DomainEvent(String eventType) {
        this.eventId = com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }
}
