package com.ecommerce.order.application.saga;

import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
import com.ecommerce.order.application.dto.StockReservation;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.repository.SagaInstanceRepository;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import com.ecommerce.order.infra.client.PaymentSyncClient;
import com.github.f4b6a3.ulid.UlidCreator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * No-saga variant: synchronous, single-transaction order flow.
 *
 * <p>Order POST blocks on Product (stock) and Payment (process) sequentially.
 * No Kafka events, no SagaInstance state machine — payment latency leaks
 * straight into the order POST p95.
 *
 * <p>Failure mode this exposes: when Payment is slow or down, every Order
 * POST waits / times out. Phase 1's SAGA + outbox-driven Kafka flow returns
 * Order in PENDING immediately and processes Payment asynchronously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ProductCatalogPort productCatalog;
    private final PaymentSyncClient paymentClient;

    @Transactional
    public Order startSaga(CreateOrderCommand command) {
        Order order = Order.create(
                command.customerId(),
                generateOrderNumber(),
                mapShippingAddress(command.shippingAddress()),
                command.memo()
        );

        List<StockReservation> reservations = new ArrayList<>();
        try {
            for (OrderItemCommand item : command.items()) {
                ProductSnapshotDto snapshot = productCatalog.fetchSnapshot(item.productVariantId());
                productCatalog.reserveStock(item.productVariantId(), item.quantity());
                reservations.add(new StockReservation(item.productVariantId(), item.quantity()));

                VariantSnapshot vs = new VariantSnapshot(
                        snapshot.productId(),
                        snapshot.productVariantId(),
                        snapshot.productName(),
                        snapshot.size(),
                        snapshot.color(),
                        snapshot.unitPrice()
                );
                order.addItem(OrderItem.create(vs, item.quantity()));
            }
        } catch (Exception e) {
            releaseAllStock(reservations);
            throw e;
        }

        orderRepository.save(order);

        // SagaInstance row is preserved for compatibility with existing
        // dashboards / queries; no state machine drives it any more.
        SagaInstance saga = SagaInstance.create(order.getId(), order.getOrderNumber());
        sagaRepository.save(saga);

        // Synchronous payment — blocks the order POST until Payment finishes.
        try {
            PaymentSyncClient.Result result = paymentClient.process(
                    order.getId(), order.getOrderNumber(), order.getTotalAmount());
            if (result.success()) {
                order.markConfirmed();
                order.markPaid();
                saga.moveToPaymentProcessing();
                saga.moveToCompleted();
                log.info("Order paid synchronously: orderNumber={}, paymentId={}, txn={}",
                        order.getOrderNumber(), result.paymentId(), result.transactionId());
            } else {
                order.cancel();
                releaseAllStock(reservations);
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                saga.moveToCompensated();
                log.warn("Payment rejected synchronously: orderNumber={}", order.getOrderNumber());
            }
        } catch (RuntimeException e) {
            // Payment unavailable — synchronous failure rolls the order back.
            order.cancel();
            releaseAllStock(reservations);
            saga.moveToPaymentProcessing();
            saga.moveToCompensating();
            saga.moveToCompensated();
            throw e;
        }

        return order;
    }

    /**
     * Retained for binary compatibility with existing call-sites; no longer
     * invoked because no-saga doesn't use Kafka events.
     */
    @SuppressWarnings("unused")
    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        // no-op
    }

    @SuppressWarnings("unused")
    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        // no-op
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

    private void releaseAllStock(List<StockReservation> reservations) {
        for (StockReservation reservation : reservations) {
            try {
                productCatalog.releaseStock(reservation.variantId(), reservation.quantity());
            } catch (Exception e) {
                log.warn("Stock release failed: variantId={}, qty={}",
                        reservation.variantId(), reservation.quantity(), e);
            }
        }
    }
}
