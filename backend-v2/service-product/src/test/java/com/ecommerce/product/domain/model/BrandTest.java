package com.ecommerce.product.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class BrandTest {

    @Test
    void create_withValidData_succeeds() {
        Brand brand = Brand.create("Iron Heart", "Japanese denim", "http://logo.png", "JP");

        assertThat(brand.getName()).isEqualTo("Iron Heart");
        assertThat(brand.getDescription()).isEqualTo("Japanese denim");
        assertThat(brand.getLogoUrl()).isEqualTo("http://logo.png");
        assertThat(brand.getCountry()).isEqualTo("JP");
    }

    @Test
    void create_withBlankName_throwsException() {
        assertThatThrownBy(() -> Brand.create("", "desc", null, "US"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_withNullName_throwsException() {
        assertThatThrownBy(() -> Brand.create(null, "desc", null, "US"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_modifiesFields() {
        Brand brand = Brand.create("TestBrand", "old desc", "old-logo.png", "US");

        brand.update("new desc", "new-logo.png", "JP");

        assertThat(brand.getDescription()).isEqualTo("new desc");
        assertThat(brand.getLogoUrl()).isEqualTo("new-logo.png");
        assertThat(brand.getCountry()).isEqualTo("JP");
        assertThat(brand.getName()).isEqualTo("TestBrand");
    }
}
