package com.ecommerce.product.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductVariantTest {

    private Product product;

    @BeforeEach
    void setUp() {
        Brand brand = Brand.create("TestBrand", "desc", null, "US");
        product = Product.create(brand, "TestProduct", "desc", BigDecimal.valueOf(100), "tops");
    }

    @Test
    void reserveStock_withSufficientStock_decrementsQuantity() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        variant.reserveStock(5);

        assertThat(variant.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void reserveStock_withInsufficientStock_throwsBusinessException() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThatThrownBy(() -> variant.reserveStock(11))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reserveStock_withZeroQuantity_throwsBusinessException() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThatThrownBy(() -> variant.reserveStock(0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reserveStock_withNegativeQuantity_throwsBusinessException() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThatThrownBy(() -> variant.reserveStock(-1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void releaseStock_incrementsQuantity() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        variant.releaseStock(3);

        assertThat(variant.getStockQuantity()).isEqualTo(13);
    }

    @Test
    void releaseStock_withZeroQuantity_throwsException() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThatThrownBy(() -> variant.releaseStock(0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void hasStock_returnsTrueWhenSufficient() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThat(variant.hasStock(10)).isTrue();
        assertThat(variant.hasStock(5)).isTrue();
    }

    @Test
    void hasStock_returnsFalseWhenInsufficient() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThat(variant.hasStock(11)).isFalse();
    }

    @Test
    void effectivePrice_returnsPriceOverrideWhenPresent() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10,
                BigDecimal.valueOf(80));

        assertThat(variant.effectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void effectivePrice_returnsProductPriceWhenNoOverride() {
        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThat(variant.effectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }
}
