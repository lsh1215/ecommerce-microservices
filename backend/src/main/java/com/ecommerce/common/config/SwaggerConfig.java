package com.ecommerce.common.config;

import com.ecommerce.common.dto.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Platform API")
                        .description("Backend API for the E-Commerce Order Platform")
                        .version("v1.0.0"));
    }

    @Bean
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    addErrorResponse(responses, "400", "Invalid input");
                    addErrorResponse(responses, "401", "Unauthorized");
                    addErrorResponse(responses, "403", "Forbidden");
                    addErrorResponse(responses, "404", "Resource not found");
                    addErrorResponse(responses, "500", "Internal server error");
                }));
    }

    private void addErrorResponse(ApiResponses responses, String statusCode, String description) {
        if (!responses.containsKey(statusCode)) {
            io.swagger.v3.oas.models.responses.ApiResponse apiResponse =
                    new io.swagger.v3.oas.models.responses.ApiResponse()
                            .description(description)
                            .content(new Content().addMediaType("application/json",
                                    new MediaType().schema(new Schema<ApiResponse<?>>().$ref("#/components/schemas/ApiResponse"))));
            responses.addApiResponse(statusCode, apiResponse);
        }
    }
}
