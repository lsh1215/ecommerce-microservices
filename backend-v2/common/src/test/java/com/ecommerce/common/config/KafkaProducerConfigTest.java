package com.ecommerce.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaProducerConfigTest {

    private KafkaProducerConfig config;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        config = new KafkaProducerConfig();
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    }

    @Test
    @DisplayName("object producer uses idempotent producer settings for at-least-once outbox publish")
    void producerFactory_usesIdempotentProducerSettings() {
        ProducerFactory<String, Object> producerFactory = config.producerFactory(meterRegistry);

        Map<String, Object> props = configurationProperties(producerFactory);

        assertThat(props)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        assertThat((Integer) props.get(ProducerConfig.RETRIES_CONFIG)).isPositive();
    }

    @Test
    @DisplayName("string producer uses the same delivery contract as object producer")
    void stringProducerFactory_usesSameDeliveryContract() {
        ProducerFactory<String, String> producerFactory = config.stringProducerFactory(meterRegistry);

        Map<String, Object> props = configurationProperties(producerFactory);

        assertThat(props)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        assertThat((Integer) props.get(ProducerConfig.RETRIES_CONFIG)).isPositive();
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, Object> configurationProperties(ProducerFactory<String, V> producerFactory) {
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
        return ((DefaultKafkaProducerFactory<String, V>) producerFactory).getConfigurationProperties();
    }
}
