package com.ecommerce.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers configuration providing a MySQL 8.x container for integration tests.
 * Datasource properties are dynamically injected via @DynamicPropertySource,
 * so no datasource block is needed in application-test.yml.
 */
@TestConfiguration
@Testcontainers
public class TestContainersConfig {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test");

    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
