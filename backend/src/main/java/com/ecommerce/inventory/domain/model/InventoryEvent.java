package com.ecommerce.inventory.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class InventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "drop_event_id")
    private Long dropEventId;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static InventoryEvent create(Long inventoryId, String eventType, String triggerType,
                                        int quantityChange, Long orderId, Long dropEventId, String reason) {
        InventoryEvent event = new InventoryEvent();
        event.inventoryId = inventoryId;
        event.eventType = eventType;
        event.triggerType = triggerType;
        event.quantityChange = quantityChange;
        event.orderId = orderId;
        event.dropEventId = dropEventId;
        event.reason = reason;
        return event;
    }
}
