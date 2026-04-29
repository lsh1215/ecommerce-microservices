package com.ecommerce.order.application.saga;

import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.ItemSnapshot;
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

/**
 * Coordinates the order SAGA. RestClient calls to service-product (stock
 * reservation, fetch snapshots, release on compensation) happen here
 * <em>outside</em> any DB transaction; DB persistence and event publishing
 * are delegated to {@link OrderPersistenceService}, whose
 * {@code @Transactional} methods are kept as short as possible.
 *
 * <p>The previous design wrapped the entire startSaga in a single
 * {@code @Transactional} that included RestClient and Kafka I/O. Under
 * concurrent load this caused HikariCP connection pool exhaustion: every
 * VU held its DB connection for the duration of cross-service network
 * round-trips, blocking other requests at {@code getConnection()}. Real
 * MySQL recommends keeping external I/O strictly outside DB transactions
 * for exactly this reason.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final ProductCatalogPort productCatalog;
    private final OrderPersistenceService persistenceService;

    /**
     * SAGA 시작 — 외부 RestClient 호출(재고 스냅샷/예약)을 먼저 끝낸 뒤
     * 좁은 DB 트랜잭션에서 Order + SagaInstance 를 영속화한다. 트랜잭션
     * 안에서는 RestClient/Kafka 등 외부 I/O 를 호출하지 않는다.
     */
    public Order startSaga(CreateOrderCommand command) {
        // 1. 외부 I/O — DB 트랜잭션 없음. 재고 스냅샷 + 비관적 락 예약.
        List<StockReservation> reservations = new ArrayList<>();
        List<ItemSnapshot> itemSnapshots = new ArrayList<>();
        try {
            for (OrderItemCommand item : command.items()) {
                ProductSnapshotDto snapshot = productCatalog.fetchSnapshot(item.productVariantId());
                productCatalog.reserveStock(item.productVariantId(), item.quantity());
                reservations.add(new StockReservation(item.productVariantId(), item.quantity()));
                itemSnapshots.add(new ItemSnapshot(snapshot, item.quantity()));
            }
        } catch (Exception e) {
            releaseAllStock(reservations);
            throw e;
        }

        // 2. 좁은 DB 트랜잭션 — order + saga INSERT + outbox row(via @TransactionalEventListener).
        try {
            return persistenceService.persistOrderAndStartSaga(command, itemSnapshots);
        } catch (Exception e) {
            // 트랜잭션 commit 실패: 이미 예약된 재고를 보상으로 풀어준다.
            releaseAllStock(reservations);
            throw e;
        }
    }

    /**
     * 결제 완료 이벤트 처리 — Kafka consumer 에서 호출. DB only 작업이라
     * persistenceService 에 위임.
     */
    public void handlePaymentCompleted(String orderNumber, Long orderId, Long paymentId,
                                       String transactionId, BigDecimal amount) {
        persistenceService.handlePaymentCompleted(orderNumber);
        log.info("SAGA 완료: orderNumber={}, paymentId={}, transactionId={}",
                orderNumber, paymentId, transactionId);
    }

    /**
     * 결제 실패 이벤트 처리 — 보상 트랜잭션. DB 상태 전이 + 재고 release
     * RestClient 호출 사이에 트랜잭션을 분리한다.
     */
    public void handlePaymentFailed(String orderNumber, Long orderId, String reason) {
        // 1. 좁은 트랜잭션: SAGA -> COMPENSATING, Order cancel, 풀어줄 재고 목록 리턴.
        List<StockReservation> toRelease = persistenceService.beginCompensation(orderNumber);

        // 2. 트랜잭션 밖: 재고 해제 (RestClient).
        for (StockReservation res : toRelease) {
            try {
                productCatalog.releaseStock(res.variantId(), res.quantity());
            } catch (Exception e) {
                log.warn("재고 해제 실패: variantId={}, qty={}",
                        res.variantId(), res.quantity(), e);
            }
        }

        // 3. 좁은 트랜잭션: SAGA -> COMPENSATED.
        persistenceService.markCompensated(orderNumber);
        log.info("SAGA 보상 완료: orderNumber={}, reason={}", orderNumber, reason);
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
