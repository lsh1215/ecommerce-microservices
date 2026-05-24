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

    /**
     * 주문과 SagaInstance의 최소 상태만 먼저 저장한다.
     *
     * <p>이 트랜잭션은 Product 호출 전에 커밋되어야 하므로,
     * 주문 항목과 결제 요청 이벤트는 재고 예약이 끝난 뒤 별도 트랜잭션에서 처리한다.
     */
    @Transactional
    public PendingOrder createPendingOrder(CreateOrderCommand command) {
        Order order = Order.create(
                command.customerId(),
                generateOrderNumber(),
                mapShippingAddress(command.shippingAddress()),
                command.memo()
        );
        Order savedOrder = orderRepository.save(order);

        SagaInstance saga = SagaInstance.create(savedOrder.getId(), savedOrder.getOrderNumber());
        sagaRepository.save(saga);
        return new PendingOrder(savedOrder.getId(), savedOrder.getOrderNumber());
    }

    /**
     * Product 재고 예약이 모두 성공한 뒤 주문 항목을 확정하고 결제 요청 이벤트를 발행한다.
     *
     * <p>이 메서드 안에서는 외부 서비스를 호출하지 않고, 로컬 상태 변경과 outbox 이벤트 발행만 수행한다.
     */
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

    /**
     * 결제 완료 이벤트를 로컬 주문 상태 전이로 반영한다.
     */
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

    /**
     * 보상 트랜잭션의 DB 구간을 시작한다.
     *
     * <p>주문 취소와 Saga 상태 전이만 처리하고, 실제 Product 재고 해제 호출은 반환 이후
     * Orchestrator가 트랜잭션 밖에서 수행한다.
     */
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

    /**
     * Product 재고 해제가 모두 끝난 뒤 Saga 보상 완료 상태를 기록한다.
     */
    @Transactional
    public void markCompensated(String orderNumber) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        saga.moveToCompensated();
    }

    /**
     * Product 재고 해제 실패 시 운영자가 재시도 대상을 식별할 수 있도록 Saga 상태에 남긴다.
     */
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
