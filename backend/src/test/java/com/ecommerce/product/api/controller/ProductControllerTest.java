package com.ecommerce.product.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductImage;
import com.ecommerce.product.domain.model.ProductTranslation;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductImageRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductTranslationRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
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
import java.util.HashMap;
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
class ProductControllerTest {

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

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductTranslationRepository productTranslationRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    private Brand brand;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productTranslationRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        brand = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
    }

    @Test
    void createProduct_shouldReturn201WithProductData() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("brandId", brand.getId());
        request.put("category", "DENIM");
        request.put("basePriceAmount", 350);
        request.put("basePriceCurrency", "USD");
        request.put("fabricWeightOz", 21.0);
        request.put("fabricType", "DENIM");
        request.put("fabricWeave", "SELVEDGE");
        request.put("name", "21oz Selvedge Denim");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.slug", is("iron-heart-21oz-selvedge-denim")))
                .andExpect(jsonPath("$.data.category", is("DENIM")))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.brandName", is("Iron Heart")));
    }

    @Test
    void createProduct_shouldReturn201WithCustomSlug() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("brandId", brand.getId());
        request.put("slug", "custom-product-slug");
        request.put("category", "SHIRT");
        request.put("basePriceAmount", 200);
        request.put("basePriceCurrency", "USD");
        request.put("name", "Heavy Flannel");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug", is("custom-product-slug")));
    }

    @Test
    void createProduct_shouldReturn409WhenSlugDuplicate() throws Exception {
        productRepository.save(Product.create(brand, "existing-slug", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));

        Map<String, Object> request = new HashMap<>();
        request.put("brandId", brand.getId());
        request.put("slug", "existing-slug");
        request.put("category", "DENIM");
        request.put("basePriceAmount", 300);
        request.put("basePriceCurrency", "USD");
        request.put("name", "Another Product");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("C006")));
    }

    @Test
    void createProduct_shouldReturn400WhenCategoryBlank() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("brandId", brand.getId());
        request.put("category", "");
        request.put("basePriceAmount", 350);
        request.put("basePriceCurrency", "USD");
        request.put("name", "Test Product");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getByPublicId_shouldReturn200WithDetailIncludingVariantsTranslationsImages() throws Exception {
        Product product = Product.create(brand, "iron-heart-detail-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", "SELVEDGE");

        ProductTranslation translation = ProductTranslation.create(product, "en", "21oz Selvedge", "Heavy denim");
        product.getTranslations().add(translation);

        ProductVariant variant = ProductVariant.create(product, "IH-DETAIL-32", "32",
                "Indigo", "#1a237e", null, null, null, null, null, null, null, null, null, null);
        product.getVariants().add(variant);

        ProductImage image = ProductImage.create(product, "https://example.com/img.jpg", (short) 0, true);
        product.getImages().add(image);

        Product saved = productRepository.save(product);

        mockMvc.perform(get("/api/products/{publicId}", saved.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.slug", is("iron-heart-detail-test")))
                .andExpect(jsonPath("$.data.variants", hasSize(1)))
                .andExpect(jsonPath("$.data.variants[0].sku", is("IH-DETAIL-32")))
                .andExpect(jsonPath("$.data.translations", hasSize(1)))
                .andExpect(jsonPath("$.data.translations[0].locale", is("en")))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].isPrimary", is(true)));
    }

    @Test
    void getByPublicId_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/products/{publicId}", "01ARZ3NDEKTSV4RRFFQ69G5FAV"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void listProducts_shouldReturn200WithPaginatedResults() throws Exception {
        productRepository.save(Product.create(brand, "product-1", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", null));
        productRepository.save(Product.create(brand, "product-2", "SHIRT", null,
                new BigDecimal("200.0000"), "USD", null, null, null, null, "COTTON", null));

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.page", is(0)));
    }

    @Test
    void listProducts_shouldFilterByCategory() throws Exception {
        productRepository.save(Product.create(brand, "denim-product", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));
        productRepository.save(Product.create(brand, "shirt-product", "SHIRT", null,
                new BigDecimal("200.0000"), "USD", null, null, null, null, null, null));

        mockMvc.perform(get("/api/products").param("category", "DENIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].category", is("DENIM")));
    }

    @Test
    void listProducts_shouldFilterByPriceRange() throws Exception {
        productRepository.save(Product.create(brand, "cheap-product", "DENIM", null,
                new BigDecimal("100.0000"), "USD", null, null, null, null, null, null));
        productRepository.save(Product.create(brand, "expensive-product", "DENIM", null,
                new BigDecimal("500.0000"), "USD", null, null, null, null, null, null));

        mockMvc.perform(get("/api/products")
                        .param("minPrice", "200")
                        .param("maxPrice", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].slug", is("expensive-product")));
    }

    @Test
    void listProducts_shouldFilterByCombination() throws Exception {
        Brand redWing = brandRepository.save(Brand.create("Red Wing", null, "US", "AMERICANA", 1905, null, null));
        productRepository.save(Product.create(brand, "ih-denim", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", "SELVEDGE"));
        productRepository.save(Product.create(redWing, "rw-boot", "ACCESSORY", null,
                new BigDecimal("320.0000"), "USD", null, null, null, null, null, null));

        mockMvc.perform(get("/api/products")
                        .param("brandId", brand.getId().toString())
                        .param("category", "DENIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].slug", is("ih-denim")));
    }

    @Test
    void searchProducts_shouldFindByKeyword() throws Exception {
        Product product = Product.create(brand, "iron-heart-denim-jacket", "OUTERWEAR", null,
                new BigDecimal("500.0000"), "USD", null, null, null, null, "DENIM", null);
        ProductTranslation translation = ProductTranslation.create(product, "en", "Denim Jacket 21oz", null);
        product.getTranslations().add(translation);
        productRepository.save(product);

        productRepository.save(Product.create(brand, "iron-heart-flannel", "SHIRT", null,
                new BigDecimal("250.0000"), "USD", null, null, null, null, "COTTON", null));

        mockMvc.perform(get("/api/products/search").param("q", "denim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].slug", is("iron-heart-denim-jacket")));
    }

    @Test
    void updateProduct_shouldReturn200WithUpdatedData() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "original-slug", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", null));

        Map<String, Object> request = new HashMap<>();
        request.put("slug", "updated-slug");
        request.put("category", "OUTERWEAR");
        request.put("basePriceAmount", 500);
        request.put("basePriceCurrency", "USD");
        request.put("fabricType", "WOOL");

        mockMvc.perform(put("/api/products/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.slug", is("updated-slug")))
                .andExpect(jsonPath("$.data.category", is("OUTERWEAR")));
    }

    @Test
    void updateProduct_shouldReturn404WhenNotFound() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("category", "DENIM");
        request.put("basePriceAmount", 350);
        request.put("basePriceCurrency", "USD");

        mockMvc.perform(put("/api/products/{id}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void deleteProduct_shouldReturn200() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "to-delete", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));

        mockMvc.perform(delete("/api/products/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void deleteProduct_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void addVariant_shouldReturn201() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "variant-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));

        Map<String, Object> request = new HashMap<>();
        request.put("sku", "IH-VT-32");
        request.put("sizeLabel", "32");
        request.put("colorName", "Indigo");
        request.put("colorHex", "#1a237e");
        request.put("measChestCm", 54.0);

        mockMvc.perform(post("/api/products/{productId}/variants", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("IH-VT-32")))
                .andExpect(jsonPath("$.data.publicId", notNullValue()));
    }

    @Test
    void addVariant_shouldReturn409WhenSkuDuplicate() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "variant-dup-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));
        productVariantRepository.save(ProductVariant.create(saved, "EXISTING-SKU", "32",
                null, null, null, null, null, null, null, null, null, null, null, null));

        Map<String, Object> request = new HashMap<>();
        request.put("sku", "EXISTING-SKU");
        request.put("sizeLabel", "34");

        mockMvc.perform(post("/api/products/{productId}/variants", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("C006")));
    }

    @Test
    void updateVariant_shouldReturn200() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "variant-update-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));
        ProductVariant variant = productVariantRepository.save(ProductVariant.create(saved, "OLD-SKU", "32",
                null, null, null, null, null, null, null, null, null, null, null, null));

        Map<String, Object> request = new HashMap<>();
        request.put("sku", "NEW-SKU");
        request.put("sizeLabel", "34");

        mockMvc.perform(put("/api/products/{productId}/variants/{variantId}", saved.getId(), variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku", is("NEW-SKU")))
                .andExpect(jsonPath("$.data.sizeLabel", is("34")));
    }

    @Test
    void deleteVariant_shouldReturn200() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "variant-delete-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));
        ProductVariant variant = productVariantRepository.save(ProductVariant.create(saved, "DEL-SKU", "32",
                null, null, null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(delete("/api/products/{productId}/variants/{variantId}", saved.getId(), variant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void addTranslation_shouldReturn201() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "trans-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));

        Map<String, Object> request = Map.of(
                "locale", "en",
                "name", "21oz Selvedge Denim",
                "description", "Heavyweight denim"
        );

        mockMvc.perform(post("/api/products/{productId}/translations", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.locale", is("en")))
                .andExpect(jsonPath("$.data.name", is("21oz Selvedge Denim")));
    }

    @Test
    void addTranslation_shouldReturn409WhenLocaleAlreadyExists() throws Exception {
        Product product = Product.create(brand, "trans-dup-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null);
        ProductTranslation translation = ProductTranslation.create(product, "en", "Existing", null);
        product.getTranslations().add(translation);
        Product saved = productRepository.save(product);

        Map<String, Object> request = Map.of(
                "locale", "en",
                "name", "Duplicate"
        );

        mockMvc.perform(post("/api/products/{productId}/translations", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("C006")));
    }

    @Test
    void addImage_shouldReturn201() throws Exception {
        Product saved = productRepository.save(Product.create(brand, "img-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));

        Map<String, Object> request = new HashMap<>();
        request.put("url", "https://example.com/image.jpg");
        request.put("sortOrder", 0);
        request.put("isPrimary", true);

        mockMvc.perform(post("/api/products/{productId}/images", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.url", is("https://example.com/image.jpg")))
                .andExpect(jsonPath("$.data.isPrimary", is(true)));
    }

    @Test
    void addImage_shouldAutoUnsetPreviousPrimary() throws Exception {
        Product product = Product.create(brand, "img-primary-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null);
        ProductImage existing = ProductImage.create(product, "https://example.com/old.jpg", (short) 0, true);
        product.getImages().add(existing);
        Product saved = productRepository.save(product);

        Map<String, Object> request = new HashMap<>();
        request.put("url", "https://example.com/new.jpg");
        request.put("sortOrder", 1);
        request.put("isPrimary", true);

        mockMvc.perform(post("/api/products/{productId}/images", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isPrimary", is(true)));
    }

    @Test
    void deleteImage_shouldReturn200() throws Exception {
        Product product = Product.create(brand, "img-delete-test", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, null, null);
        ProductImage image = ProductImage.create(product, "https://example.com/del.jpg", (short) 0, false);
        product.getImages().add(image);
        Product saved = productRepository.save(product);
        Long imageId = saved.getImages().get(0).getId();

        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", saved.getId(), imageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void listProducts_paginationShouldWorkCorrectly() throws Exception {
        for (int i = 0; i < 5; i++) {
            productRepository.save(Product.create(brand, "product-page-" + i, "DENIM", null,
                    new BigDecimal("350.0000"), "USD", null, null, null, null, null, null));
        }

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(5)))
                .andExpect(jsonPath("$.data.totalPages", is(3)))
                .andExpect(jsonPath("$.data.last", is(false)));

        mockMvc.perform(get("/api/products")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.last", is(true)));
    }
}
