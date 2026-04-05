package com.ecommerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VariantSnapshotTest {

    @Test
    void equalsByValue() {
        VariantSnapshot s1 = new VariantSnapshot(1L, 10L, "Iron Heart 888S", "32", "Indigo", new BigDecimal("320.00"));
        VariantSnapshot s2 = new VariantSnapshot(1L, 10L, "Iron Heart 888S", "32", "Indigo", new BigDecimal("320.00"));

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void notEqual_whenFieldsDiffer() {
        VariantSnapshot s1 = new VariantSnapshot(1L, 10L, "Iron Heart 888S", "32", "Indigo", new BigDecimal("320.00"));
        VariantSnapshot s2 = new VariantSnapshot(1L, 11L, "Iron Heart 888S", "32", "Indigo", new BigDecimal("320.00"));

        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void equalsByValue_withNullSizeAndColor() {
        VariantSnapshot s1 = new VariantSnapshot(1L, 10L, "Belt", null, null, new BigDecimal("80.00"));
        VariantSnapshot s2 = new VariantSnapshot(1L, 10L, "Belt", null, null, new BigDecimal("80.00"));

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }
}
