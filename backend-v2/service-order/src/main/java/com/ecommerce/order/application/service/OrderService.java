package com.ecommerce.order.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.request.OrderItemRequest;
import com.ecommerce.order.api.dto.request.ShippingAddressRequest;
import com.ecommerce.order.application.dto.PaymentResult;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.StockReservation;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.service.CustomerDirectoryPort;
import com.ecommerce.order.domain.service.PaymentRequestPort;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import com.github.f4b6a3.ulid.UlidCreator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductCatalogPort productCatalog;
    private final CustomerDirectoryPort customerDirectory;
    private final PaymentRequestPort paymentRequest;

    // DELIBERATE FLAW: wraps HTTP calls in DB transaction
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        List<StockReservation> reservations = new ArrayList<>();

        try {
            customerDirectory.ensureExists(request.customerId());

            Order order = Order.create(
                    request.customerId(),
                    generateOrderNumber(),
                    mapShippingAddress(request.shippingAddress()),
                    request.memo()
            );

            for (OrderItemRequest item : request.items()) {
                ProductSnapshotDto snapshot = productCatalog.fetchSnapshot(item.productVariantId());
                productCatalog.reserveStock(item.productVariantId(), item.quantity());
                reservations.add(new StockReservation(item.productVariantId(), item.quantity()));

                VariantSnapshot variantSnapshot = new VariantSnapshot(
                        snapshot.productId(),
                        snapshot.productVariantId(),
                        snapshot.productName(),
                        snapshot.size(),
                        snapshot.color(),
                        snapshot.unitPrice()
                );
                order.addItem(OrderItem.create(variantSnapshot, item.quantity()));
            }

            orderRepository.save(order);

            PaymentResult paymentResult = paymentRequest.requestPayment(
                    order.getId(), order.getOrderNumber(), order.getTotalAmount()
            );

            if (paymentResult.success()) {
                order.markConfirmed();
            } else {
                order.cancel();
                releaseAllStock(reservations);
                throw new BusinessException(OrderErrorCode.PAYMENT_FAILED);
            }

            return order;
        } catch (BusinessException e) {
            releaseAllStock(reservations);
            throw e;
        } catch (Exception e) {
            releaseAllStock(reservations);
            throw new RuntimeException("Order creation failed", e);
        }
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    public Page<Order> getMyOrders(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        order.cancel();
        return order;
    }

    @Transactional
    public Order markPaid(Long id) {
        Order order = getOrder(id);
        order.markPaid();
        return order;
    }

    @Transactional
    public Order markConfirmed(Long id) {
        Order order = getOrder(id);
        order.markConfirmed();
        return order;
    }

    private String generateOrderNumber() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    private ShippingAddress mapShippingAddress(ShippingAddressRequest dto) {
        return new ShippingAddress(
                dto.recipientName(),
                dto.phone(),
                dto.zipCode(),
                dto.address1(),
                dto.address2()
        );
    }

    // DELIBERATE FLAW: compensation can fail silently
    private void releaseAllStock(List<StockReservation> reservations) {
        for (StockReservation reservation : reservations) {
            try {
                productCatalog.releaseStock(reservation.variantId(), reservation.quantity());
            } catch (Exception ignored) {
                log.warn("Failed to release stock for variant {}, quantity {}",
                        reservation.variantId(), reservation.quantity());
            }
        }
    }
}
