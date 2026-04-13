package com.ecommerce.order.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient productRestClient(
            @Value("${app.services.product.url}") String baseUrl,
            @Value("${app.rest-client.connect-timeout:5000}") int connectTimeout,
            @Value("${app.rest-client.read-timeout:10000}") int readTimeout) {
        return buildRestClient(baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient customerRestClient(
            @Value("${app.services.customer.url}") String baseUrl,
            @Value("${app.rest-client.connect-timeout:5000}") int connectTimeout,
            @Value("${app.rest-client.read-timeout:10000}") int readTimeout) {
        return buildRestClient(baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient paymentRestClient(
            @Value("${app.services.payment.url}") String baseUrl,
            @Value("${app.rest-client.connect-timeout:5000}") int connectTimeout,
            @Value("${app.rest-client.read-timeout:10000}") int readTimeout) {
        return buildRestClient(baseUrl, connectTimeout, readTimeout);
    }

    private RestClient buildRestClient(String baseUrl, int connectTimeout, int readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
