package com.ecommerce.payment.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.PaymentAttemptHistory;
import com.ecommerce.payment.domain.model.PaymentAttemptHistoryType;
import com.ecommerce.payment.domain.model.PaymentAttemptStatus;
import com.ecommerce.payment.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class PaymentAttemptRepositoryTest {

    @Autowired
    private PaymentAttemptRepository attemptRepository;

    @Autowired
    private PaymentAttemptHistoryRepository historyRepository;

    @Test
    @Transactional
    void should_persist_attempt_and_history_then_claim_requested_attempt() {
        PaymentAttempt attempt = PaymentAttempt.request(
                1L, "ORD-001", BigDecimal.valueOf(100), PaymentMethod.CARD);
        PaymentAttempt savedAttempt = attemptRepository.saveAndFlush(attempt);
        historyRepository.saveAndFlush(PaymentAttemptHistory.of(
                savedAttempt, PaymentAttemptHistoryType.REQUESTED));

        PaymentAttempt claimable = attemptRepository.findFirstByStatusInOrderByRequestedAtAsc(
                        List.of(PaymentAttemptStatus.REQUESTED))
                .orElseThrow();
        claimable.markProcessing();
        historyRepository.saveAndFlush(PaymentAttemptHistory.of(
                claimable, PaymentAttemptHistoryType.PROCESSING_STARTED));

        assertThat(claimable.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(historyRepository.count()).isEqualTo(2);
    }
}
