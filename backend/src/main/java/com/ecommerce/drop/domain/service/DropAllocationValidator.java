package com.ecommerce.drop.domain.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.drop.domain.repository.DropProductRepository;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DropAllocationValidator {

    private final DropProductRepository dropProductRepository;
    private final InventoryRepository inventoryRepository;

    public void validate(Long productVariantId, int requestedQuantity) {
        Inventory inventory = inventoryRepository.findByProductVariantId(productVariantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", productVariantId));

        int currentAllocated = dropProductRepository.sumAllocatedQuantityByVariantId(productVariantId);
        int totalAfterAllocation = currentAllocated + requestedQuantity;

        if (totalAfterAllocation > inventory.getQuantityAvailable()) {
            throw new BusinessException(ErrorCode.DROP_ALLOCATION_EXCEEDED,
                    "Total allocation (" + totalAfterAllocation +
                            ") exceeds available inventory (" + inventory.getQuantityAvailable() + ")");
        }
    }
}
