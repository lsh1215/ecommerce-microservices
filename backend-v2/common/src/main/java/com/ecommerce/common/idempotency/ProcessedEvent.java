package com.ecommerce.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(String eventId, String eventType) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.eventId = eventId;
        pe.eventType = eventType;
        pe.processedAt = LocalDateTime.now();
        return pe;
    }
}
