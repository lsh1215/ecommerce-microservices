package com.ecommerce.order.domain.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;

public interface CustomerDirectoryPort {
    boolean existsCustomer(Long customerId);

    default void ensureExists(Long customerId) {
        if (!existsCustomer(customerId)) {
            throw new BusinessException(OrderErrorCode.CUSTOMER_NOT_FOUND);
        }
    }
}
