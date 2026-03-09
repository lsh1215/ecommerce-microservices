package com.ecommerce.inventory.api.dto.response;

import com.ecommerce.inventory.domain.model.InventoryEvent;

import java.time.LocalDateTime;

public record InventoryEventResponse(
        Long id,
        String eventType,
        String triggerType,
        int quantityChange,
        Long orderId,
        Long dropEventId,
        String reason,
        LocalDateTime createdAt
) {
    public static InventoryEventResponse from(InventoryEvent event) {
        return new InventoryEventResponse(
                event.getId(),
                event.getEventType(),
                event.getTriggerType(),
                event.getQuantityChange(),
                event.getOrderId(),
                event.getDropEventId(),
                event.getReason(),
                event.getCreatedAt()
        );
    }
}
