package com.ecommerce.customer.application.dto;

public record RegisterCustomerCommand(
        String email,
        String password,
        String name,
        String phone
) {}
