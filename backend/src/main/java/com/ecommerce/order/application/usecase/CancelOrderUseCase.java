package com.ecommerce.order.application.usecase;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.inventory.application.service.InventoryService;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatusHistory;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING", "CONFIRMED");

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Orders execute(String publicId, String reason) {
        Orders order = orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Order", publicId));

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseWithRetry(
                    item.getProductVariantId(),
                    item.getQuantity(),
                    order.getId(),
                    null,
                    "Order cancelled"
            );
        }

        String previousStatus = order.getStatus();
        order.transitionTo("CANCELLED");

        OrderStatusHistory history = OrderStatusHistory.create(previousStatus, "CANCELLED", reason);
        order.addStatusHistory(history);

        return orderRepository.save(order);
    }
}
