package com.ecommerce.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaConsumerConfigTest {

    private KafkaConsumerConfig config;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        config = new KafkaConsumerConfig();
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "test-group");
    }

    @Test
    @DisplayName("object consumer disables auto commit for listener-managed retry and DLT handling")
    void consumerFactory_disablesAutoCommit() {
        ConsumerFactory<String, Object> consumerFactory = config.consumerFactory(meterRegistry);

        Map<String, Object> props = configurationProperties(consumerFactory);

        assertThat(props)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    @Test
    @DisplayName("string consumer disables auto commit for listener-managed retry and DLT handling")
    void stringConsumerFactory_disablesAutoCommit() {
        ConsumerFactory<String, String> consumerFactory = config.stringConsumerFactory(meterRegistry);

        Map<String, Object> props = configurationProperties(consumerFactory);

        assertThat(props)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    @Test
    @DisplayName("listener factories install the shared error handler and observation")
    void listenerFactories_installErrorHandlerAndObservation() {
        ConsumerFactory<String, String> consumerFactory = config.stringConsumerFactory(meterRegistry);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(kafkaTemplate);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.stringKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
        assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler")).isSameAs(errorHandler);
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, Object> configurationProperties(ConsumerFactory<String, V> consumerFactory) {
        assertThat(consumerFactory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        return ((DefaultKafkaConsumerFactory<String, V>) consumerFactory).getConfigurationProperties();
    }
}
