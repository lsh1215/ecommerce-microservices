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

    /**
     * SAGA 시작: 주문 생성 -> 재고 예약(동기) -> 이벤트 발행(비동기 결제 트리거).
     * Payment 서비스가 다운이어도 주문은 PENDING으로 생성됨.
     */
    @Transactional
    public Order startSaga(CreateOrderCommand command) {
        // 고객 검증은 Traefik forwardAuth middleware가 service-customer로
        // 위임하여 ingress 단계에서 끝남. 여기서는 X-Customer-Id 헤더로
        // 전달된 customerId를 trust하고 곧장 주문 Aggregate 생성으로 진입.

        // 주문 Aggregate 생성
        Order order = Order.create(
                command.customerId(),
                generateOrderNumber(),
                mapShippingAddress(command.shippingAddress()),
                command.memo()
        );

        // 3단계: 재고 예약 (동기 - Product 서비스, 즉시 일관성 필요)
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

        // 4단계: SAGA 인스턴스 생성
        SagaInstance saga = SagaInstance.create(order.getId(), order.getOrderNumber());
        sagaRepository.save(saga);

        // 5단계: 결제 요청 이벤트 발행 (비동기 - Kafka)
        // Payment 서비스가 다운이어도 Kafka에 메시지가 쌓여서 복구 시 처리됨
        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getOrderNumber(),
                command.customerId(), order.getTotalAmount()));

        saga.moveToPaymentProcessing();

        return order;
        // 주문은 PENDING 상태로 즉시 반환. 결제는 비동기로 처리됨.
    }

    /**
     * 결제 완료 이벤트 수신 -> 주문 상태를 CONFIRMED -> PAID로 전이.
     */
    @Transactional
    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // FSM: PENDING -> CONFIRMED -> PAID (두 단계를 한 트랜잭션에서 실행)
        order.markConfirmed();
        order.markPaid();
        saga.moveToCompleted();

        log.info("SAGA 완료: orderNumber={}, paymentId={}, transactionId={}",
                orderNumber, paymentId, transactionId);
    }

    /**
     * 결제 실패 이벤트 수신 -> 주문 취소 + 재고 해제 (보상 트랜잭션).
     */
    @Transactional
    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        saga.moveToCompensating();

        // 보상: 주문 취소
        order.cancel();

        // 보상: 예약된 재고 해제 (동기 - Product 서비스)
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
