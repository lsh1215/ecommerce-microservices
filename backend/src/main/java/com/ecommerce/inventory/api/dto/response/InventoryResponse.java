package com.ecommerce.inventory.api.dto.response;

import com.ecommerce.inventory.domain.model.Inventory;

import java.time.LocalDateTime;

public record InventoryResponse(
        Long id,
        Long productVariantId,
        int quantityAvailable,
        int quantityReserved,
        int quantitySold,
        LocalDateTime updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductVariantId(),
                inventory.getQuantityAvailable(),
                inventory.getQuantityReserved(),
                inventory.getQuantitySold(),
                inventory.getUpdatedAt()
        );
    }
}
