package com.ecommerce.order.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.domain.service.VirtualAccountIssuer;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    public static final Duration DEFAULT_EXPIRATION_DURATION = Duration.ofDays(7);

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Embedded
    private ShippingAddress shippingAddress;

    private String memo;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Embedded
    private VirtualAccountInstruction virtualAccount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public static Order create(Long customerId, String orderNumber,
                               ShippingAddress address, String memo) {
        Order order = new Order();
        order.customerId = customerId;
        order.orderNumber = orderNumber != null ? orderNumber : UlidCreator.getMonotonicUlid().toString();
        order.shippingAddress = address;
        order.memo = memo;
        order.expiresAt = LocalDateTime.now().plus(DEFAULT_EXPIRATION_DURATION);
        return order;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
        recalculateTotalAmount();
    }

    public void assignVirtualAccount(VirtualAccountIssuer issuer) {
        this.virtualAccount = issuer.issue(this.orderNumber, this.totalAmount, this.expiresAt);
    }

    public void markConfirmed() {
        validateTransition(OrderStatus.CONFIRMED);
        this.status = OrderStatus.CONFIRMED;
    }

    public void markPaid() {
        validateTransition(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
    }

    public void markShipping() {
        validateTransition(OrderStatus.SHIPPING);
        this.status = OrderStatus.SHIPPING;
    }

    public void markDelivered() {
        validateTransition(OrderStatus.DELIVERED);
        this.status = OrderStatus.DELIVERED;
    }

    public void cancel() {
        validateTransition(OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
    }

    private void validateTransition(OrderStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
    }

    private void recalculateTotalAmount() {
        this.totalAmount = this.items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
