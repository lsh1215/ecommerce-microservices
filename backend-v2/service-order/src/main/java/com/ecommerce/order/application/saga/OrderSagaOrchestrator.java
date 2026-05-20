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

    public Order startSaga(CreateOrderCommand command) {
        PendingOrder pendingOrder = transactions.createPendingOrder(command);
        List<ReservedOrderItem> reservedItems = new ArrayList<>();
        List<StockReservation> reservations = new ArrayList<>();
        try {
            for (OrderItemCommand item : command.items()) {
                ProductSnapshotDto snapshot = productCatalog.fetchSnapshot(item.productVariantId());
                productCatalog.reserveStock(item.productVariantId(), item.quantity());
                reservedItems.add(new ReservedOrderItem(snapshot, item.quantity()));
                reservations.add(new StockReservation(item.productVariantId(), item.quantity()));
            }
        } catch (Exception e) {
            releaseAllStock(reservations);
            transactions.markStockReservationFailed(pendingOrder.orderId(), reservations, e);
            throw e;
        }
        return transactions.completeStockReservation(pendingOrder.orderId(), reservedItems);
    }

    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        transactions.completePayment(orderNumber, orderId, paymentId, transactionId, amount);
    }

    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        List<StockReservation> reservations = transactions.startCompensation(orderNumber);
        try {
            for (StockReservation reservation : reservations) {
                productCatalog.releaseStock(reservation.variantId(), reservation.quantity());
            }
        } catch (Exception e) {
            transactions.markCompensationRetryRequired(orderNumber, e);
            throw e;
        }
        transactions.markCompensated(orderNumber);
        log.info("SAGA compensation completed: orderNumber={}, reason={}", orderNumber, reason);
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
