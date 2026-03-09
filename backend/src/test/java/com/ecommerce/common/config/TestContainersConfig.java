package com.ecommerce.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared Testcontainers configuration providing a MySQL 8.x container for integration tests.
 *
 * Usage in test classes:
 * <pre>
 *   &#64;Import(TestContainersConfig.class)
 *   &#64;DynamicPropertySource
 *   static void overrideProperties(DynamicPropertyRegistry registry) {
 *       registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
 *       registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
 *       registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
 *   }
 * </pre>
 *
 * Note: @DynamicPropertySource must be declared in the test class itself (Spring limitation).
 */
@TestConfiguration
public class TestContainersConfig {

    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--max-connections=500");

    static {
        MYSQL.start();
    }
}
