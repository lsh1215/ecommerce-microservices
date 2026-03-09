package com.ecommerce.drop.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateDropEventRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt
) {}
