package com.ecommerce.common.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    @Test
    void softDelete_shouldSetDeletedTrue() {
        TestEntity entity = new TestEntity();
        assertThat(entity.isDeleted()).isFalse();

        entity.softDelete();
        assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    void restore_shouldSetDeletedFalse() {
        TestEntity entity = new TestEntity();
        entity.softDelete();
        entity.restore();
        assertThat(entity.isDeleted()).isFalse();
    }
}
