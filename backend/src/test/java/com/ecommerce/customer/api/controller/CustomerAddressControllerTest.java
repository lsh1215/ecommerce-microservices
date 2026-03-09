package com.ecommerce.customer.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class CustomerAddressControllerTest {

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

    @Autowired
    private CustomerAddressRepository addressRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        customer = customerRepository.save(
                Customer.create("addr@example.com", BCrypt.hashpw("pw", BCrypt.gensalt()), "Addr User"));
    }

    private Map<String, Object> buildAddressRequest(String label, boolean isDefault) {
        Map<String, Object> req = new HashMap<>();
        req.put("label", label);
        req.put("recipientName", "John Doe");
        req.put("phone", "010-1234-5678");
        req.put("street", "123 Main St");
        req.put("detail", "Apt 4B");
        req.put("city", "Seoul");
        req.put("stateProvince", "Seoul");
        req.put("postalCode", "12345");
        req.put("country", "KR");
        req.put("isDefault", isDefault);
        return req;
    }

    @Test
    void addAddress_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/customers/{customerId}/addresses", customer.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildAddressRequest("Home", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.recipientName", is("John Doe")))
                .andExpect(jsonPath("$.data.isDefault", is(true)));
    }

    @Test
    void addAddress_shouldReturn400WhenValidationFails() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("recipientName", "");
        req.put("phone", "");
        req.put("street", "");
        req.put("city", "");
        req.put("postalCode", "");
        req.put("country", "");
        req.put("isDefault", false);

        mockMvc.perform(post("/api/customers/{customerId}/addresses", customer.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void listAddresses_shouldReturnAllAddressesForCustomer() throws Exception {
        addressRepository.save(CustomerAddress.create(customer, "Home", "John", "010-1111-1111",
                "Street 1", null, "Seoul", null, "11111", "KR", true));
        addressRepository.save(CustomerAddress.create(customer, "Office", "John", "010-2222-2222",
                "Street 2", null, "Busan", null, "22222", "KR", false));

        mockMvc.perform(get("/api/customers/{customerId}/addresses", customer.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void updateAddress_shouldReturn200WithUpdatedData() throws Exception {
        CustomerAddress address = addressRepository.save(CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Old St", null, "Seoul", null, "11111", "KR", false));

        Map<String, Object> req = buildAddressRequest("Office", false);
        req.put("recipientName", "Jane Doe");
        req.put("city", "Busan");

        mockMvc.perform(put("/api/customers/{customerId}/addresses/{addressId}",
                        customer.getPublicId(), address.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.label", is("Office")))
                .andExpect(jsonPath("$.data.recipientName", is("Jane Doe")))
                .andExpect(jsonPath("$.data.city", is("Busan")));
    }

    @Test
    void deleteAddress_shouldReturn200() throws Exception {
        CustomerAddress address = addressRepository.save(CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Street", null, "Seoul", null, "11111", "KR", false));

        mockMvc.perform(delete("/api/customers/{customerId}/addresses/{addressId}",
                        customer.getPublicId(), address.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertThat(addressRepository.findByPublicId(address.getPublicId())).isEmpty();
    }

    @Test
    void addAddress_shouldToggleDefault() throws Exception {
        CustomerAddress first = addressRepository.save(CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Street 1", null, "Seoul", null, "11111", "KR", true));

        mockMvc.perform(post("/api/customers/{customerId}/addresses", customer.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildAddressRequest("Office", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault", is(true)));

        CustomerAddress reloaded = addressRepository.findById(first.getId()).orElseThrow();
        assertThat(reloaded.isDefault()).isFalse();
    }

    @Test
    void deleteAddress_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(delete("/api/customers/{customerId}/addresses/{addressId}",
                        customer.getPublicId(), "01HX0000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
