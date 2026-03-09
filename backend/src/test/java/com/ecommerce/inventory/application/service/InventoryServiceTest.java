package com.ecommerce.inventory.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.model.InventoryEvent;
import com.ecommerce.inventory.domain.repository.InventoryEventRepository;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryEventRepository inventoryEventRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory buildInventory(Long variantId, int available, int reserved, int sold) {
        Inventory inv = Inventory.create(variantId);
        for (int i = 0; i < available; i++) inv.adjust(1);
        return inv;
    }

    @Test
    void createForVariant_shouldSaveInventoryWithZeroQuantities() {
        given(inventoryRepository.existsByProductVariantId(1L)).willReturn(false);
        given(inventoryRepository.save(any(Inventory.class))).willAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.createForVariant(1L);

        assertThat(result.getProductVariantId()).isEqualTo(1L);
        assertThat(result.getQuantityAvailable()).isZero();
        assertThat(result.getQuantityReserved()).isZero();
        assertThat(result.getQuantitySold()).isZero();
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    void createForVariant_shouldThrowWhenAlreadyExists() {
        given(inventoryRepository.existsByProductVariantId(1L)).willReturn(true);

        assertThatThrownBy(() -> inventoryService.createForVariant(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_ENTITY));

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserve_shouldDecreaseAvailableAndIncreaseReserved() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(10);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        inventoryService.reserve(1L, 3, null, null);

        assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
        assertThat(inventory.getQuantityReserved()).isEqualTo(3);

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(inventoryEventRepository).save(eventCaptor.capture());
        InventoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("RESERVED");
        assertThat(event.getTriggerType()).isEqualTo("SYSTEM");
        assertThat(event.getQuantityChange()).isEqualTo(-3);
    }

    @Test
    void reserve_shouldThrowInsufficientStockWhenAvailableLessThanRequested() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(2);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserve(1L, 5, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        verify(inventoryRepository, never()).save(any());
        verify(inventoryEventRepository, never()).save(any());
    }

    @Test
    void reserve_shouldThrowWhenInventoryNotFound() {
        given(inventoryRepository.findByProductVariantId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(99L, 1, null, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deduct_shouldDecreaseReservedAndIncreaseSold() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(10);
        inventory.reserve(5);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        inventoryService.deduct(1L, 5, 42L);

        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(inventory.getQuantitySold()).isEqualTo(5);

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(inventoryEventRepository).save(eventCaptor.capture());
        InventoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("DEDUCTED");
        assertThat(event.getTriggerType()).isEqualTo("SYSTEM");
        assertThat(event.getQuantityChange()).isEqualTo(-5);
        assertThat(event.getOrderId()).isEqualTo(42L);
    }

    @Test
    void release_shouldDecreaseReservedAndIncreaseAvailable() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(10);
        inventory.reserve(5);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        inventoryService.release(1L, 5, 42L, null, "Order cancelled");

        assertThat(inventory.getQuantityAvailable()).isEqualTo(10);
        assertThat(inventory.getQuantityReserved()).isZero();

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(inventoryEventRepository).save(eventCaptor.capture());
        InventoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("RELEASED");
        assertThat(event.getQuantityChange()).isEqualTo(5);
        assertThat(event.getReason()).isEqualTo("Order cancelled");
    }

    @Test
    void release_shouldUseSagaCompensationTriggerWhenReasonContainsCompensation() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(10);
        inventory.reserve(5);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        inventoryService.release(1L, 5, 42L, null, "saga compensation rollback");

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(inventoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTriggerType()).isEqualTo("SAGA_COMPENSATION");
    }

    @Test
    void adjust_shouldChangeAvailableByDelta() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(5);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.adjust(1L, 10, "Restock");

        assertThat(result.getQuantityAvailable()).isEqualTo(15);

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(inventoryEventRepository).save(eventCaptor.capture());
        InventoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("ADJUSTMENT");
        assertThat(event.getTriggerType()).isEqualTo("ADMIN");
        assertThat(event.getQuantityChange()).isEqualTo(10);
        assertThat(event.getReason()).isEqualTo("Restock");
    }

    @Test
    void adjust_shouldAllowNegativeDelta() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(20);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.adjust(1L, -5, "Stock correction");

        assertThat(result.getQuantityAvailable()).isEqualTo(15);
    }

    @Test
    void reserveWithRetry_shouldRetryOnOptimisticLockException() {
        Inventory inventory = Inventory.create(1L);
        inventory.adjust(10);
        given(inventoryRepository.findByProductVariantId(1L))
                .willThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L))
                .willThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L))
                .willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(inventoryEventRepository.save(any())).willAnswer(i -> i.getArgument(0));

        inventoryService.reserveWithRetry(1L, 3, null, null);

        verify(inventoryRepository, times(3)).findByProductVariantId(1L);
    }

    @Test
    void reserveWithRetry_shouldThrowInventoryLockFailedAfterMaxRetries() {
        given(inventoryRepository.findByProductVariantId(1L))
                .willThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L));

        assertThatThrownBy(() -> inventoryService.reserveWithRetry(1L, 1, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_LOCK_FAILED));

        verify(inventoryRepository, times(3)).findByProductVariantId(1L);
    }

    @Test
    void findByVariantId_shouldReturnInventory() {
        Inventory inventory = Inventory.create(1L);
        given(inventoryRepository.findByProductVariantId(1L)).willReturn(Optional.of(inventory));

        Inventory result = inventoryService.findByVariantId(1L);

        assertThat(result.getProductVariantId()).isEqualTo(1L);
    }

    @Test
    void findByVariantId_shouldThrowWhenNotFound() {
        given(inventoryRepository.findByProductVariantId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.findByVariantId(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findEvents_shouldReturnEventsForInventory() {
        given(inventoryRepository.existsById(1L)).willReturn(true);
        InventoryEvent event = InventoryEvent.create(1L, "ADJUSTMENT", "ADMIN", 10, null, null, "test");
        given(inventoryEventRepository.findByInventoryIdOrderByCreatedAtDesc(1L)).willReturn(List.of(event));

        List<InventoryEvent> result = inventoryService.findEvents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("ADJUSTMENT");
    }

    @Test
    void findEvents_shouldThrowWhenInventoryNotFound() {
        given(inventoryRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> inventoryService.findEvents(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
