package com.ecommerce.payment.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import com.ecommerce.payment.domain.repository.PaymentEventRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class PaymentControllerTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void processPayment_shouldReturn200WithPaymentData() throws Exception {
        Map<String, Object> request = Map.of(
                "orderId", 1,
                "amount", 29900,
                "currency", "KRW",
                "idempotencyKey", "idem-ctrl-001",
                "paymentMethod", "CARD"
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.orderId", is(1)))
                .andExpect(jsonPath("$.data.amount", is(29900)))
                .andExpect(jsonPath("$.data.currency", is("KRW")))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.paymentMethod", is("CARD")));
    }

    @Test
    void processPayment_shouldReturnExistingForDuplicateIdempotencyKey() throws Exception {
        Map<String, Object> request = Map.of(
                "orderId", 2,
                "amount", 100,
                "currency", "USD",
                "idempotencyKey", "idem-dup-ctrl"
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    void processPayment_shouldReturn400WhenMissingRequiredFields() throws Exception {
        Map<String, Object> request = Map.of(
                "orderId", 1
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByPublicId_shouldReturn200WhenFound() throws Exception {
        Map<String, Object> request = Map.of(
                "orderId", 3,
                "amount", 5000,
                "currency", "JPY",
                "idempotencyKey", "idem-get-001"
        );

        String responseBody = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String publicId = objectMapper.readTree(responseBody).get("data").get("publicId").asText();

        mockMvc.perform(get("/api/payments/{publicId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", is(publicId)))
                .andExpect(jsonPath("$.data.orderId", is(3)));
    }

    @Test
    void getByPublicId_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/{publicId}", "non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getByOrderId_shouldReturn200WhenFound() throws Exception {
        Map<String, Object> request = Map.of(
                "orderId", 99,
                "amount", 200,
                "currency", "USD",
                "idempotencyKey", "idem-order-001"
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/payments/order/{orderId}", 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderId", is(99)));
    }

    @Test
    void getByOrderId_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/order/{orderId}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void refundPayment_shouldReturn200ForCompletedPayment() throws Exception {
        Map<String, Object> createRequest = Map.of(
                "orderId", 10,
                "amount", 500,
                "currency", "USD",
                "idempotencyKey", "idem-refund-001"
        );

        String responseBody = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String publicId = objectMapper.readTree(responseBody).get("data").get("publicId").asText();

        Map<String, Object> refundRequest = Map.of("reason", "Customer request");

        mockMvc.perform(post("/api/payments/{publicId}/refund", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("REFUNDED")));
    }

    @Test
    void refundPayment_shouldReturn400ForPendingPayment() throws Exception {
        Payment pending = paymentRepository.save(
                Payment.create(20L, "idem-pend-refund", BigDecimal.valueOf(100), "USD", null));

        Map<String, Object> refundRequest = Map.of("reason", "Test");

        mockMvc.perform(post("/api/payments/{publicId}/refund", pending.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void refundPayment_shouldReturn404WhenNotFound() throws Exception {
        Map<String, Object> refundRequest = Map.of("reason", "Test");

        mockMvc.perform(post("/api/payments/{publicId}/refund", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
