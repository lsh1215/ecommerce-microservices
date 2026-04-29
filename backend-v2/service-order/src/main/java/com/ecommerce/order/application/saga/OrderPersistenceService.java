package com.ecommerce.order.application.saga;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.ItemSnapshot;
import com.ecommerce.order.application.dto.OrderItemCommand;
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
import com.github.f4b6a3.ulid.UlidCreator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-only side of the order SAGA, kept in a separate bean so its
 * {@code @Transactional} scope stays narrow. RestClient calls and any other
 * external I/O must happen in {@link OrderSagaOrchestrator} <em>outside</em>
 * a transaction; this bean only persists state and publishes Spring
 * application events (which are wired to outbox handlers under
 * {@code BEFORE_COMMIT} so the outbox row INSERT is part of the same DB
 * transaction without forcing Kafka I/O into it).
 */
@Component
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Persists Order + SagaInstance and publishes OrderCreatedEvent. Stock
     * has already been reserved at service-product before entering this
     * method, and the variant snapshots have been fetched.
     */
    @Transactional
    public Order persistOrderAndStartSaga(
            CreateOrderCommand command,
            List<ItemSnapshot> itemSnapshots) {
        Order order = Order.create(
                command.customerId(),
                generateOrderNumber(),
                mapShippingAddress(command.shippingAddress()),
                command.memo()
        );
        for (ItemSnapshot is : itemSnapshots) {
            VariantSnapshot vs = new VariantSnapshot(
                    is.snapshot().productId(),
                    is.snapshot().productVariantId(),
                    is.snapshot().productName(),
                    is.snapshot().size(),
                    is.snapshot().color(),
                    is.snapshot().unitPrice()
            );
            order.addItem(OrderItem.create(vs, is.quantity()));
        }

        orderRepository.save(order);

        SagaInstance saga = SagaInstance.create(order.getId(), order.getOrderNumber());
        sagaRepository.save(saga);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getOrderNumber(),
                command.customerId(), order.getTotalAmount()));

        saga.moveToPaymentProcessing();
        return order;
    }

    /**
     * Begins compensation: marks SAGA as compensating, cancels the order,
     * returns the list of stock reservations that need to be released. The
     * actual {@code productCatalog.releaseStock} calls are issued by the
     * orchestrator <em>after</em> this transaction commits.
     */
    @Transactional
    public List<StockReservation> beginCompensation(String orderNumber) {
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

    /** Marks SAGA compensated after stock release calls finish. */
    @Transactional
    public void markCompensated(String orderNumber) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        saga.moveToCompensated();
    }

    @Transactional
    public void handlePaymentCompleted(String orderNumber) {
        SagaInstance saga = sagaRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Order order = orderRepository.findById(saga.getOrderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        order.markConfirmed();
        order.markPaid();
        saga.moveToCompleted();
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
