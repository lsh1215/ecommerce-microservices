package com.ecommerce.drop.domain.model;

import com.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DropProductTest {

    private DropProduct createProduct(int allocatedQuantity) {
        DropEvent event = DropEvent.create("Drop", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        return DropProduct.create(event, 1L, allocatedQuantity,
                new BigDecimal("299.99"), "USD");
    }

    @Test
    void create_shouldInitializeWithZeroSoldQuantity() {
        DropProduct product = createProduct(10);

        assertThat(product.getProductVariantId()).isEqualTo(1L);
        assertThat(product.getAllocatedQuantity()).isEqualTo(10);
        assertThat(product.getSoldQuantity()).isZero();
        assertThat(product.getDropPriceAmount()).isEqualByComparingTo("299.99");
        assertThat(product.getDropPriceCurrency()).isEqualTo("USD");
    }

    @Test
    void sell_shouldIncreaseSoldQuantity() {
        DropProduct product = createProduct(10);

        product.sell(3);

        assertThat(product.getSoldQuantity()).isEqualTo(3);
    }

    @Test
    void sell_shouldAllowSellingUpToAllocatedQuantity() {
        DropProduct product = createProduct(5);

        product.sell(3);
        product.sell(2);

        assertThat(product.getSoldQuantity()).isEqualTo(5);
    }

    @Test
    void sell_shouldThrowWhenExceedingAllocatedQuantity() {
        DropProduct product = createProduct(5);
        product.sell(3);

        assertThatThrownBy(() -> product.sell(3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sell_shouldThrowWhenSellingMoreThanRemaining() {
        DropProduct product = createProduct(10);
        product.sell(8);

        assertThatThrownBy(() -> product.sell(5))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sell_shouldThrowWhenAllocatedQuantityIsZero() {
        DropProduct product = createProduct(0);

        assertThatThrownBy(() -> product.sell(1))
                .isInstanceOf(BusinessException.class);
    }
}
