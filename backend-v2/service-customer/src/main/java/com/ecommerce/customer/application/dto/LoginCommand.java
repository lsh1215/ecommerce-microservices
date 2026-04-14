package com.ecommerce.customer.application.dto;

public record LoginCommand(
        String email,
        String password
) {}
