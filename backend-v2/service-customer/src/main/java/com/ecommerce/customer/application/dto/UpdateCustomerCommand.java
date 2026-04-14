package com.ecommerce.customer.application.dto;

public record UpdateCustomerCommand(
        String name,
        String phone
) {}
