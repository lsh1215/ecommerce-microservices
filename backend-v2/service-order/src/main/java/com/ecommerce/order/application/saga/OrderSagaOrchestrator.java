package com.ecommerce.order.application.saga;

import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.StockReservation;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderSagaTransactions transactions;
    private final ProductCatalogPort productCatalog;

    /**
     * 주문 SAGA 시작점.
     *
     * <p>Order DB 트랜잭션 안에서 Product 서비스를 호출하지 않도록,
     * 로컬 주문 생성과 재고 예약 완료 처리를 각각 짧은 트랜잭션으로 나눈다.
     * Product 스냅샷 조회와 재고 예약은 두 트랜잭션 사이에서 실행된다.
     */
    public Order startSaga(CreateOrderCommand command) {
        PendingOrder pendingOrder = transactions.createPendingOrder(command);
        List<ReservedOrderItem> reservedItems = new ArrayList<>();
        List<StockReservation> reservations = new ArrayList<>();
        try {
            for (OrderItemCommand item : command.items()) {
                // 외부 I/O 구간: Order DB 트랜잭션이 열려 있지 않아야 한다.
                ProductSnapshotDto snapshot = productCatalog.fetchSnapshot(item.productVariantId());
                productCatalog.reserveStock(pendingOrder.orderId(), item.productVariantId(), item.quantity());
                reservedItems.add(new ReservedOrderItem(snapshot, item.quantity()));
                reservations.add(new StockReservation(
                        pendingOrder.orderId(), item.productVariantId(), item.quantity()));
            }
        } catch (Exception e) {
            // 일부 재고만 예약된 실패 케이스이므로 이미 예약한 항목만 즉시 보상한다.
            releaseAllStock(reservations);
            transactions.markStockReservationFailed(pendingOrder.orderId(), reservations, e);
            throw e;
        }
        return transactions.completeStockReservation(pendingOrder.orderId(), reservedItems);
    }

    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        List<StockReservation> reservations = transactions.findReservations(orderNumber);
        for (StockReservation reservation : reservations) {
            productCatalog.confirmReservation(reservation.orderId(), reservation.variantId());
        }
        transactions.completePayment(orderNumber, orderId, paymentId, transactionId, amount);
    }

    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        List<StockReservation> reservations = transactions.startCompensation(orderNumber);
        try {
            for (StockReservation reservation : reservations) {
                // 보상 재고 해제도 Product 서비스 호출이므로 Order DB 트랜잭션 밖에서 실행한다.
                productCatalog.releaseStock(reservation.orderId(), reservation.variantId(), reservation.quantity());
            }
        } catch (Exception e) {
            // 실패를 삼키면 재고 보상이 누락되므로 Saga 상태에 재시도 필요를 남긴다.
            transactions.markCompensationRetryRequired(orderNumber, e);
            throw e;
        }
        transactions.markCompensated(orderNumber);
        log.info("SAGA compensation completed: orderNumber={}, reason={}", orderNumber, reason);
    }

    private void releaseAllStock(List<StockReservation> reservations) {
        for (StockReservation reservation : reservations) {
            try {
                productCatalog.releaseStock(reservation.orderId(), reservation.variantId(), reservation.quantity());
            } catch (Exception e) {
                log.warn("Stock release failed: variantId={}, qty={}",
                        reservation.variantId(), reservation.quantity(), e);
            }
        }
    }
}
