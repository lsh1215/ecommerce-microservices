package com.ecommerce.product.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Brand createBrand() {
        return Brand.create("TestBrand", "desc", null, "US");
    }

    @Test
    void create_withValidData_succeeds() {
        Brand brand = createBrand();

        Product product = Product.create(brand, "Oxford Shirt", "Classic fit",
                BigDecimal.valueOf(120), "shirts");

        assertThat(product.getName()).isEqualTo("Oxford Shirt");
        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getBrand()).isEqualTo(brand);
        assertThat(product.getCategory()).isEqualTo("shirts");
        assertThat(product.getVariants()).isEmpty();
        assertThat(product.getImages()).isEmpty();
    }

    @Test
    void create_withBlankName_throwsException() {
        Brand brand = createBrand();

        assertThatThrownBy(() -> Product.create(brand, "", "desc", BigDecimal.TEN, "shirts"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_withNullBrand_throwsException() {
        assertThatThrownBy(() -> Product.create(null, "Shirt", "desc", BigDecimal.TEN, "shirts"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_withNegativePrice_throwsException() {
        Brand brand = createBrand();

        assertThatThrownBy(() -> Product.create(brand, "Shirt", "desc",
                BigDecimal.valueOf(-1), "shirts"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addVariant_addsToList() {
        Brand brand = createBrand();
        Product product = Product.create(brand, "Shirt", "desc", BigDecimal.TEN, "shirts");

        ProductVariant variant = product.addVariant("SKU-001", "M", "Black", 10, null);

        assertThat(product.getVariants()).hasSize(1);
        assertThat(variant.getSku()).isEqualTo("SKU-001");
        assertThat(variant.getProduct()).isEqualTo(product);
    }

    @Test
    void addImage_addsToList() {
        Brand brand = createBrand();
        Product product = Product.create(brand, "Shirt", "desc", BigDecimal.TEN, "shirts");

        ProductImage image = product.addImage("http://img.com/1.jpg", 0, true);

        assertThat(product.getImages()).hasSize(1);
        assertThat(image.isPrimary()).isTrue();
    }

    @Test
    void addImage_withPrimary_clearsPreviousPrimary() {
        Brand brand = createBrand();
        Product product = Product.create(brand, "Shirt", "desc", BigDecimal.TEN, "shirts");

        ProductImage first = product.addImage("http://img.com/1.jpg", 0, true);
        ProductImage second = product.addImage("http://img.com/2.jpg", 1, true);

        assertThat(first.isPrimary()).isFalse();
        assertThat(second.isPrimary()).isTrue();
    }

    @Test
    void activate_setsStatusActive() {
        Brand brand = createBrand();
        Product product = Product.create(brand, "Shirt", "desc", BigDecimal.TEN, "shirts");
        product.deactivate();

        product.activate();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void deactivate_setsStatusInactive() {
        Brand brand = createBrand();
        Product product = Product.create(brand, "Shirt", "desc", BigDecimal.TEN, "shirts");

        product.deactivate();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }
}
