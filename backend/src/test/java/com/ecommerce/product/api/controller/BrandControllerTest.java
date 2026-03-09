package com.ecommerce.product.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.repository.BrandRepository;
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

import java.util.Map;

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
class BrandControllerTest {

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
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository.deleteAll();
    }

    @Test
    void listBrands_shouldReturn200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listBrands_shouldReturn200WithAllBrands() throws Exception {
        brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
        brandRepository.save(Brand.create("Red Wing", null, "US", "AMERICANA", 1905, null, null));

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void getBySlug_shouldReturn200WithBrandData() throws Exception {
        brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, "Premium denim", null));

        mockMvc.perform(get("/api/brands/{slug}", "iron-heart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Iron Heart")))
                .andExpect(jsonPath("$.data.slug", is("iron-heart")))
                .andExpect(jsonPath("$.data.countryOfOrigin", is("JP")))
                .andExpect(jsonPath("$.data.styleCategory", is("WORKWEAR")))
                .andExpect(jsonPath("$.data.publicId", notNullValue()));
    }

    @Test
    void getBySlug_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/brands/{slug}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createBrand_shouldReturn201WithBrandData() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Carhartt",
                "countryOfOrigin", "US",
                "styleCategory", "WORKWEAR",
                "foundedYear", 1889
        );

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Carhartt")))
                .andExpect(jsonPath("$.data.slug", is("carhartt")))
                .andExpect(jsonPath("$.data.publicId", notNullValue()));
    }

    @Test
    void createBrand_shouldReturn201WithCustomSlug() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Iron Heart",
                "slug", "ih-japan",
                "countryOfOrigin", "JP",
                "styleCategory", "WORKWEAR"
        );

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug", is("ih-japan")));
    }

    @Test
    void createBrand_shouldReturn409WhenNameDuplicate() throws Exception {
        brandRepository.save(Brand.create("Red Wing", null, "US", "AMERICANA", 1905, null, null));

        Map<String, Object> request = Map.of(
                "name", "Red Wing",
                "countryOfOrigin", "US",
                "styleCategory", "AMERICANA"
        );

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("C006")));
    }

    @Test
    void createBrand_shouldReturn409WhenSlugDuplicate() throws Exception {
        brandRepository.save(Brand.create("Red Wing", "classic-boot", "US", "AMERICANA", 1905, null, null));

        Map<String, Object> request = Map.of(
                "name", "Different Brand",
                "slug", "classic-boot",
                "countryOfOrigin", "US",
                "styleCategory", "AMERICANA"
        );

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("C006")));
    }

    @Test
    void createBrand_shouldReturn400WhenNameBlank() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "",
                "countryOfOrigin", "US"
        );

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void updateBrand_shouldReturn200WithUpdatedData() throws Exception {
        Brand saved = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));

        Map<String, Object> request = Map.of(
                "name", "Iron Heart Premium",
                "countryOfOrigin", "JP",
                "styleCategory", "WORKWEAR",
                "foundedYear", 1975,
                "description", "Updated description"
        );

        mockMvc.perform(put("/api/brands/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Iron Heart Premium")))
                .andExpect(jsonPath("$.data.slug", is("iron-heart-premium")))
                .andExpect(jsonPath("$.data.description", is("Updated description")));
    }

    @Test
    void updateBrand_shouldReturn404WhenNotFound() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Nonexistent Brand",
                "countryOfOrigin", "US",
                "styleCategory", "AMERICANA"
        );

        mockMvc.perform(put("/api/brands/{id}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void deleteBrand_shouldReturn200() throws Exception {
        Brand saved = brandRepository.save(Brand.create("Carhartt", null, "US", "WORKWEAR", 1889, null, null));

        mockMvc.perform(delete("/api/brands/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void deleteBrand_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(delete("/api/brands/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
