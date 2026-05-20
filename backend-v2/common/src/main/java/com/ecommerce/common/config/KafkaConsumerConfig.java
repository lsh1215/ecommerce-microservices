package com.ecommerce.common.config;

import com.ecommerce.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer config with Micrometer instrumentation + a tuned
 * {@link DefaultErrorHandler}.
 *
 * <p>{@link MicrometerConsumerListener} is registered so {@code kafka_consumer_*}
 * client-level metrics (records-consumed-rate, fetch-latency, etc.) are exposed.
 * The container factory ALSO honors Spring Kafka's listener-side Observation when
 * {@code spring.kafka.listener.observation-enabled=true} (Spring Kafka 3.x), which
 * emits {@code spring_kafka_listener_seconds{result=success|failure}} — the canonical
 * consumer-side success/failure timer.
 *
 * <p>The {@code DefaultErrorHandler} bean below is the second half of the
 * "drop RuntimeException wrapping in listeners" change. The listeners now let
 * the original exception type propagate; this handler is the consumer of that
 * type information:
 * <ul>
 *   <li><b>Non-retryable</b> exceptions (poison pill: malformed JSON,
 *       deserialization failure, illegal-argument from validation) are sent
 *       straight to the {@code <topic>.DLT} topic — retrying the same message
 *       cannot produce a different outcome.</li>
 *   <li><b>Retryable</b> exceptions (Kafka broker unavailable, transient
 *       service errors, optimistic-lock collisions in the consumer's domain
 *       layer) get an exponential backoff: 500ms → 1s → 2s → 4s → 8s, then DLT.
 *       Capped retries prevent an unrelated downstream outage from monopolizing
 *       a partition.</li>
 * </ul>
 *
 * <p>Without this bean, Spring Kafka falls back to its default
 * {@code FixedBackOff(0, 9)} — 9 immediate retries with no DLT — which silently
 * commits and loses the message after exhaustion. That default is what the
 * previous {@code throw new RuntimeException(msg, e)} wrapper happened to mask;
 * removing the wrapper without configuring the handler would have made
 * runtime behavior strictly worse.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:${spring.application.name:default-group}}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(MeterRegistry meterRegistry) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.*");
        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(props);
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        return cf;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory(MeterRegistry meterRegistry) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        return cf;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory(
            ConsumerFactory<String, String> stringConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /**
     * 5 retries with exponential backoff (500ms → 8s, capped), then DLT.
     * Non-retryable exceptions (malformed payload, validation failure) skip
     * retry and go straight to {@code <topic>.DLT} — retrying poison pills
     * just delays the inevitable.
     *
     * <p>The DLT topic name is derived as {@code <originalTopic>.DLT} by
     * {@link DeadLetterPublishingRecoverer}'s default destination resolver.
     * Operators should pre-create those topics; if they don't exist the
     * recoverer logs a warning and the partition pauses until manual
     * intervention.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> stringKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                stringKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(8000L);
        backOff.setMaxElapsedTime(30000L); // ≈ 5 attempts within 30s, then DLT

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                DeserializationException.class,
                IllegalArgumentException.class,
                BusinessException.class
        );
        handler.setCommitRecovered(true);
        return handler;
    }
}
