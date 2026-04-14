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
     * 재고 예약 및 결제 처리를 포함한 신규 주문을 생성한다.
     * Customer, Product, Payment 서비스를 단일 트랜잭션 내에서 동기 호출로 조율한다.
     * 의도적 결함: @Transactional이 동기 HTTP 호출을 감싸고 있어 DB 커넥션을 오래 점유한다.
     */
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        List<StockReservation> reservations = new ArrayList<>();

        try {
            // 1단계: 고객 존재 여부 검증 (Customer 서비스로 동기 HTTP 호출)
            customerDirectory.ensureExists(command.customerId());

            // 2단계: 배송지를 포함한 주문 Aggregate 생성
            Order order = Order.create(
                    command.customerId(),
                    generateOrderNumber(),
                    mapShippingAddress(command.shippingAddress()),
                    command.memo()
            );

            // 3단계: 항목별 재고 예약 (각 Variant에 대해 Product 서비스로 동기 HTTP 호출)
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

            // 4단계: 결제 요청 (Payment 서비스로 동기 HTTP 호출)
            PaymentResult paymentResult = paymentRequest.requestPayment(
                    order.getId(), order.getOrderNumber(), order.getTotalAmount()
            );

            // 5단계: 결제 결과에 따라 주문 확정 또는 취소
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

    /** 주문을 PAID 상태로 전이한다 (Payment 서비스 콜백 시 호출). */
    @Transactional
    public Order markPaid(Long id) {
        Order order = getOrder(id);
        order.markPaid();
        return order;
    }

    /** 주문을 CONFIRMED 상태로 전이한다 (결제 검증 후 호출). */
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
     * 실패 시 재고 보상을 최선으로 시도한다.
     * 의도적 결함: 보상 자체가 조용히 실패할 수 있어 유령 예약이 남을 수 있다.
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
