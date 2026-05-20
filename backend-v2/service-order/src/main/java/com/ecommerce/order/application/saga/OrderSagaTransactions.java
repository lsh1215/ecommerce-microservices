package com.ecommerce.order.application.saga;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
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
import com.ecommerce.order.domain.service.VirtualAccountIssuer;
import com.github.f4b6a3.ulid.UlidCreator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaTransactions {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final VirtualAccountIssuer virtualAccountIssuer;

    @Transactional
    public PendingOrder createPendingOrder(CreateOrderCommand command) {
        Order order = Order.create(
                command.customerId(),
                generateOrderNumber(),
                mapShippingAddress(command.shippingAddress()),
                command.memo()
        );
        orderRepository.saveAndFlush(order);

        SagaInstance saga = SagaInstance.create(order.getId(), order.getOrderNumber());
        sagaRepository.save(saga);
        return new PendingOrder(order.getId(), order.getOrderNumber());
    }

    @Transactional
    public Order completeStockReservation(Long orderId, List<ReservedOrderItem> reservedItems) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        SagaInstance saga = sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        for (ReservedOrderItem item : reservedItems) {
            order.addItem(OrderItem.create(toVariantSnapshot(item), item.quantity()));
        }
        order.assignVirtualAccount(virtualAccountIssuer);
        saga.moveToStockReserved();
        saga.moveToPaymentProcessing();
        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getOrderNumber(),
                order.getCustomerId(), order.getTotalAmount()));
        return order;
    }

    @Transactional
    public void markStockReservationFailed(Long orderId, List<StockReservation> reservations, Exception cause) {
        SagaInstance saga = sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        saga.moveToStockReservationFailed(buildReservationFailureReason(reservations, cause));
    }

    @Transactional
    public void completePayment(String orderNumber, Long orderId, Long paymentId,
                                String transactionId, BigDecimal amount) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        order.markConfirmed();
        order.markPaid();
        saga.moveToCompleted();

        log.info("SAGA completed: orderNumber={}, paymentId={}, transactionId={}",
                orderNumber, paymentId, transactionId);
    }

    @Transactional
    public List<StockReservation> startCompensation(String orderNumber) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        saga.moveToCompensating();
        order.cancel();
        return order.getItems().stream()
                .map(item -> new StockReservation(
                        item.getVariantSnapshot().getProductVariantId(),
                        item.getQuantity()))
                .toList();
    }

    @Transactional
    public void markCompensated(String orderNumber) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        saga.moveToCompensated();
    }

    @Transactional
    public void markCompensationRetryRequired(String orderNumber, Exception cause) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        saga.moveToCompensationRetryRequired(cause.getMessage());
    }

    private VariantSnapshot toVariantSnapshot(ReservedOrderItem item) {
        return new VariantSnapshot(
                item.snapshot().productId(),
                item.snapshot().productVariantId(),
                item.snapshot().productName(),
                item.snapshot().size(),
                item.snapshot().color(),
                item.snapshot().unitPrice()
        );
    }

    private String buildReservationFailureReason(List<StockReservation> reservations, Exception cause) {
        String reserved = reservations.stream()
                .map(r -> r.variantId() + "x" + r.quantity())
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        return "stock reservation failed after reserved=[" + reserved + "]: " + cause.getMessage();
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
}
