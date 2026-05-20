package com.ecommerce.payment.domain.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class PaymentRepositoryBusinessIdempotencyTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void orderIdIsUniqueBusinessKey() {
        paymentRepository.saveAndFlush(Payment.create(
                1L, "ORD-001", BigDecimal.valueOf(100), PaymentMethod.CARD));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(Payment.create(
                1L, "ORD-001-DUP", BigDecimal.valueOf(100), PaymentMethod.CARD)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
