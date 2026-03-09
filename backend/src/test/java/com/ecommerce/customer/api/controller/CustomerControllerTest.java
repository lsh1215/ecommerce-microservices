package com.ecommerce.customer.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
class CustomerControllerTest {

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
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void register_shouldReturn201WithCustomerData() throws Exception {
        Map<String, String> request = Map.of(
                "email", "reg@example.com",
                "password", "password123",
                "name", "Reg User"
        );

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.email", is("reg@example.com")))
                .andExpect(jsonPath("$.data.name", is("Reg User")))
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    void register_shouldReturn409WhenEmailDuplicate() throws Exception {
        Customer existing = Customer.create("dup@example.com", BCrypt.hashpw("pw", BCrypt.gensalt()), "Existing");
        customerRepository.save(existing);

        Map<String, String> request = Map.of(
                "email", "dup@example.com",
                "password", "password123",
                "name", "New"
        );

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("CU001")));
    }

    @Test
    void register_shouldReturn400WhenValidationFails() throws Exception {
        Map<String, String> request = Map.of(
                "email", "not-an-email",
                "password", "short",
                "name", ""
        );

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void login_shouldReturn200WithLoginResponse() throws Exception {
        Customer customer = Customer.create("login@example.com",
                BCrypt.hashpw("password123", BCrypt.gensalt()), "Login User");
        customerRepository.save(customer);

        Map<String, String> request = Map.of(
                "email", "login@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.name", is("Login User")))
                .andExpect(jsonPath("$.data.email", is("login@example.com")));
    }

    @Test
    void login_shouldReturn401WhenCredentialsInvalid() throws Exception {
        Customer customer = Customer.create("login@example.com",
                BCrypt.hashpw("password123", BCrypt.gensalt()), "Login User");
        customerRepository.save(customer);

        Map<String, String> request = Map.of(
                "email", "login@example.com",
                "password", "wrongpassword"
        );

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("CU002")));
    }

    @Test
    void getById_shouldReturn200WithCustomerData() throws Exception {
        Customer customer = Customer.create("get@example.com",
                BCrypt.hashpw("password123", BCrypt.gensalt()), "Get User");
        Customer saved = customerRepository.save(customer);

        mockMvc.perform(get("/api/customers/{id}", saved.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("get@example.com")))
                .andExpect(jsonPath("$.data.name", is("Get User")));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", "01HX0000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
