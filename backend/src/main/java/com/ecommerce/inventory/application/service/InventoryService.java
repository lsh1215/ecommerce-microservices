package com.ecommerce.inventory.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.model.InventoryEvent;
import com.ecommerce.inventory.domain.repository.InventoryEventRepository;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryEventRepository inventoryEventRepository;

    @Transactional
    public Inventory createForVariant(Long productVariantId) {
        if (inventoryRepository.existsByProductVariantId(productVariantId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY,
                    "Inventory already exists for variant: " + productVariantId);
        }
        Inventory inventory = Inventory.create(productVariantId);
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Inventory findByVariantId(Long productVariantId) {
        return inventoryRepository.findByProductVariantId(productVariantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", productVariantId));
    }

    @Transactional(readOnly = true)
    public List<InventoryEvent> findEvents(Long inventoryId) {
        if (!inventoryRepository.existsById(inventoryId)) {
            throw new EntityNotFoundException("Inventory", inventoryId);
        }
        return inventoryEventRepository.findByInventoryIdOrderByCreatedAtDesc(inventoryId);
    }

    public void reserveWithRetry(Long variantId, int quantity, Long orderId, Long dropEventId) {
        int maxRetries = 3;
        long[] delays = {50, 100, 200};
        for (int i = 0; i < maxRetries; i++) {
            try {
                reserve(variantId, quantity, orderId, dropEventId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (i == maxRetries - 1) {
                    throw new BusinessException(ErrorCode.INVENTORY_LOCK_FAILED);
                }
                try {
                    Thread.sleep(delays[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(Long variantId, int quantity, Long orderId, Long dropEventId) {
        Inventory inventory = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", variantId));

        if (inventory.getQuantityAvailable() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        inventory.reserve(quantity);
        inventoryRepository.save(inventory);

        InventoryEvent event = InventoryEvent.create(
                inventory.getId(), "RESERVED", "SYSTEM", -quantity, orderId, dropEventId, null);
        inventoryEventRepository.save(event);
    }

    public void deductWithRetry(Long variantId, int quantity, Long orderId) {
        int maxRetries = 3;
        long[] delays = {50, 100, 200};
        for (int i = 0; i < maxRetries; i++) {
            try {
                deduct(variantId, quantity, orderId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (i == maxRetries - 1) {
                    throw new BusinessException(ErrorCode.INVENTORY_LOCK_FAILED);
                }
                try {
                    Thread.sleep(delays[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deduct(Long variantId, int quantity, Long orderId) {
        Inventory inventory = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", variantId));

        inventory.deduct(quantity);
        inventoryRepository.save(inventory);

        InventoryEvent event = InventoryEvent.create(
                inventory.getId(), "DEDUCTED", "SYSTEM", -quantity, orderId, null, null);
        inventoryEventRepository.save(event);
    }

    public void releaseWithRetry(Long variantId, int quantity, Long orderId, Long dropEventId, String reason) {
        int maxRetries = 3;
        long[] delays = {50, 100, 200};
        for (int i = 0; i < maxRetries; i++) {
            try {
                release(variantId, quantity, orderId, dropEventId, reason);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (i == maxRetries - 1) {
                    throw new BusinessException(ErrorCode.INVENTORY_LOCK_FAILED);
                }
                try {
                    Thread.sleep(delays[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long variantId, int quantity, Long orderId, Long dropEventId, String reason) {
        Inventory inventory = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", variantId));

        inventory.release(quantity);
        inventoryRepository.save(inventory);

        String triggerType = (reason != null && reason.contains("compensation")) ? "SAGA_COMPENSATION" : "SYSTEM";
        InventoryEvent event = InventoryEvent.create(
                inventory.getId(), "RELEASED", triggerType, quantity, orderId, dropEventId, reason);
        inventoryEventRepository.save(event);
    }

    @Transactional
    public Inventory adjust(Long variantId, int delta, String reason) {
        Inventory inventory = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for variant", variantId));

        inventory.adjust(delta);
        Inventory saved = inventoryRepository.save(inventory);

        InventoryEvent event = InventoryEvent.create(
                inventory.getId(), "ADJUSTMENT", "ADMIN", delta, null, null, reason);
        inventoryEventRepository.save(event);

        return saved;
    }
}
