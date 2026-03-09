package com.ecommerce.order.application.usecase;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.infrastructure.application.service.ExchangeRateService;
import com.ecommerce.inventory.application.service.InventoryService;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.request.OrderItemRequest;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatusHistory;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final ExchangeRateService exchangeRateService;

    @Transactional
    public Orders execute(CreateOrderRequest request) {
        if (orderRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }

        Orders order = Orders.create(
                request.customerId(),
                request.idempotencyKey(),
                BigDecimal.ZERO,
                request.currency(),
                request.shippingAddress()
        );

        for (OrderItemRequest itemRequest : request.items()) {
            ProductVariant variant = productVariantRepository.findById(itemRequest.productVariantId())
                    .orElseThrow(() -> new EntityNotFoundException("ProductVariant", itemRequest.productVariantId()));

            String productName = variant.getProduct().getSlug();
            String brandName = variant.getProduct().getBrand().getName();
            BigDecimal basePrice = variant.getPriceOverrideAmount() != null
                    ? variant.getPriceOverrideAmount()
                    : variant.getProduct().getBasePriceAmount();
            String baseCurrency = variant.getPriceOverrideCurrency() != null
                    ? variant.getPriceOverrideCurrency()
                    : variant.getProduct().getBasePriceCurrency();

            BigDecimal convertedPrice = exchangeRateService.convert(basePrice, baseCurrency, request.currency());

            inventoryService.reserveWithRetry(variant.getId(), itemRequest.quantity(), null, null);

            OrderItem item = OrderItem.create(
                    variant.getId(),
                    itemRequest.quantity(),
                    productName,
                    brandName,
                    convertedPrice,
                    request.currency(),
                    variant.getSizeLabel(),
                    variant.getSku()
            );
            order.addItem(item);
        }

        order.recalculateTotal();

        OrderStatusHistory history = OrderStatusHistory.create(null, "PENDING", null);
        order.addStatusHistory(history);

        return orderRepository.save(order);
    }
}
