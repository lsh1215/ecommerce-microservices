package com.ecommerce.order.application.saga;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
import com.ecommerce.order.application.dto.StockReservation;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.repository.SagaInstanceRepository;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import com.ecommerce.order.domain.service.VirtualAccountIssuer;
import com.github.f4b6a3.ulid.UlidCreator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ProductCatalogPort productCatalog;
    private final ApplicationEventPublisher eventPublisher;
    private final VirtualAccountIssuer virtualAccountIssuer;

    /** Starts order creation, stock reservation, and asynchronous payment request publishing. */
    @Transactional
    public Order startSaga(CreateOrderCommand command) {
        // Customer identity has already been validated by the ingress forward-auth layer.

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

        order.assignVirtualAccount(virtualAccountIssuer);

        orderRepository.save(order);

        SagaInstance saga = SagaInstance.create(order.getId(), order.getOrderNumber());
        sagaRepository.save(saga);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getOrderNumber(),
                command.customerId(), order.getTotalAmount()));

        saga.moveToPaymentProcessing();

        return order;
    }

    /** Handles a completed payment event and advances the order and saga state. */
    @Transactional
    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        order.markConfirmed();
        order.markPaid();
        saga.moveToCompleted();

        log.info("SAGA 완료: orderNumber={}, paymentId={}, transactionId={}",
                orderNumber, paymentId, transactionId);
    }

    /** Handles a failed payment event with order cancellation and stock release compensation. */
    @Transactional
    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        saga.moveToCompensating();

        order.cancel();

        for (OrderItem item : order.getItems()) {
            try {
                productCatalog.releaseStock(
                        item.getVariantSnapshot().getProductVariantId(),
                        item.getQuantity());
            } catch (Exception e) {
                log.warn("재고 해제 실패: variantId={}, qty={}",
                        item.getVariantSnapshot().getProductVariantId(), item.getQuantity(), e);
            }
        }

        saga.moveToCompensated();

        log.info("SAGA 보상 완료: orderNumber={}, reason={}", orderNumber, reason);
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
                log.warn("재고 해제 실패: variantId={}, qty={}",
                        reservation.variantId(), reservation.quantity(), e);
            }
        }
    }
}
