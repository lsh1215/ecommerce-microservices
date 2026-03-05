package com.ecommerce.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing configuration.
 * Enables @CreatedDate and @LastModifiedDate annotations on entities.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
