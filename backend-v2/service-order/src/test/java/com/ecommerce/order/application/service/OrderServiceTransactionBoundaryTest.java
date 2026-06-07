package com.ecommerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OrderServiceTransactionBoundaryTest {

    @Test
    @DisplayName("주문 생성 시작점은 외부 API 호출을 감싸는 Order 트랜잭션을 열지 않는다")
    void createOrder_disablesOuterTransaction() throws Exception {
        Method method = OrderService.class.getMethod("createOrder",
                com.ecommerce.order.application.dto.CreateOrderCommand.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
