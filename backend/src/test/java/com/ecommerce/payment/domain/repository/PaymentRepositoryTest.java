package com.ecommerce.payment.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class PaymentRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @BeforeEach
    void setUp() {
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void save_shouldPersistPaymentWithGeneratedFields() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(29900), "KRW", "CARD");

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getPublicId()).hasSize(26);
        assertThat(saved.getOrderId()).isEqualTo(1L);
        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(29900));
        assertThat(saved.getCurrency()).isEqualTo("KRW");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPaymentMethod()).isEqualTo("CARD");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByPublicId_shouldReturnPaymentWhenExists() {
        Payment payment = paymentRepository.save(
                Payment.create(1L, "idem-002", BigDecimal.valueOf(100), "USD", null));

        Optional<Payment> found = paymentRepository.findByPublicId(payment.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
    }

    @Test
    void findByPublicId_shouldReturnEmptyWhenNotExists() {
        Optional<Payment> found = paymentRepository.findByPublicId("non-existent-id");

        assertThat(found).isEmpty();
    }

    @Test
    void findByOrderId_shouldReturnPaymentWhenExists() {
        paymentRepository.save(
                Payment.create(42L, "idem-003", BigDecimal.valueOf(5000), "JPY", "BANK"));

        Optional<Payment> found = paymentRepository.findByOrderId(42L);

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(42L);
    }

    @Test
    void findByOrderId_shouldReturnEmptyWhenNotExists() {
        Optional<Payment> found = paymentRepository.findByOrderId(999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdempotencyKey_shouldReturnPaymentWhenExists() {
        paymentRepository.save(
                Payment.create(1L, "unique-key-123", BigDecimal.valueOf(200), "USD", null));

        Optional<Payment> found = paymentRepository.findByIdempotencyKey("unique-key-123");

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo("unique-key-123");
    }

    @Test
    void findByIdempotencyKey_shouldReturnEmptyWhenNotExists() {
        Optional<Payment> found = paymentRepository.findByIdempotencyKey("no-such-key");

        assertThat(found).isEmpty();
    }

    @Test
    void paymentEvent_shouldPersistWithCreatedAt() {
        Payment payment = paymentRepository.save(
                Payment.create(1L, "idem-evt", BigDecimal.valueOf(100), "USD", null));

        PaymentEvent event = PaymentEvent.create(payment, "INITIATED", BigDecimal.valueOf(100), "USD");
        PaymentEvent saved = paymentEventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPayment().getId()).isEqualTo(payment.getId());
        assertThat(saved.getEventType()).isEqualTo("INITIATED");
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPaymentIdOrderByCreatedAtAsc_shouldReturnEventsInOrder() {
        Payment payment = paymentRepository.save(
                Payment.create(1L, "idem-evts", BigDecimal.valueOf(100), "USD", null));

        paymentEventRepository.save(PaymentEvent.create(payment, "INITIATED", BigDecimal.valueOf(100), "USD"));
        paymentEventRepository.save(PaymentEvent.create(payment, "COMPLETED", BigDecimal.valueOf(100), "USD"));

        List<PaymentEvent> events = paymentEventRepository
                .findByPaymentIdOrderByCreatedAtAsc(payment.getId());

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("INITIATED");
        assertThat(events.get(1).getEventType()).isEqualTo("COMPLETED");
    }
}
