package com.ecommerce.drop.api.dto.response;

import com.ecommerce.drop.domain.model.DropEvent;

import java.time.LocalDateTime;

public record DropEventResponse(
        String publicId,
        String title,
        String description,
        String status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DropEventResponse from(DropEvent event) {
        return new DropEventResponse(
                event.getPublicId(),
                event.getTitle(),
                event.getDescription(),
                event.getStatus(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
