package com.ecommerce.order.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.PaymentResult;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
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

    /**
     * Create a new order with stock reservation and payment processing.
     * Coordinates across Customer, Product, and Payment services in a single transaction.
     * DELIBERATE FLAW: @Transactional wraps synchronous HTTP calls, causing long-held DB connections.
     */
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        List<StockReservation> reservations = new ArrayList<>();

        try {
            // Step 1: Validate customer exists (sync HTTP to Customer service)
            customerDirectory.ensureExists(command.customerId());

            // Step 2: Build order aggregate with shipping address
            Order order = Order.create(
                    command.customerId(),
                    generateOrderNumber(),
                    mapShippingAddress(command.shippingAddress()),
                    command.memo()
            );

            // Step 3: Reserve stock per item (sync HTTP to Product service per variant)
            for (OrderItemCommand item : command.items()) {
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

            // Step 4: Request payment (sync HTTP to Payment service)
            PaymentResult paymentResult = paymentRequest.requestPayment(
                    order.getId(), order.getOrderNumber(), order.getTotalAmount()
            );

            // Step 5: Confirm or cancel based on payment result
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

    /** Transition order to PAID state (called by Payment service callback). */
    @Transactional
    public Order markPaid(Long id) {
        Order order = getOrder(id);
        order.markPaid();
        return order;
    }

    /** Transition order to CONFIRMED state (called after payment verification). */
    @Transactional
    public Order markConfirmed(Long id) {
        Order order = getOrder(id);
        order.markConfirmed();
        return order;
    }

    private String generateOrderNumber() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    private ShippingAddress mapShippingAddress(ShippingAddressCommand dto) {
        return new ShippingAddress(
                dto.recipientName(),
                dto.phone(),
                dto.zipCode(),
                dto.address1(),
                dto.address2()
        );
    }

    /**
     * Best-effort stock compensation on failure.
     * DELIBERATE FLAW: compensation can fail silently, leaving phantom reservations.
     */
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
