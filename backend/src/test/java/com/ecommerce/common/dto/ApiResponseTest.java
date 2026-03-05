package com.ecommerce.common.dto;

import com.ecommerce.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApiResponse factory methods and JSON serialization.
 */
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successWithData_shouldReturnSuccessTrueAndData() {
        String data = "test-data";
        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
    }

    @Test
    void successWithoutData_shouldReturnSuccessTrueAndNullData() {
        ApiResponse<?> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void errorWithCodeAndMessage_shouldReturnSuccessFalseAndErrorDetail() {
        ApiResponse<Void> response = ApiResponse.error("E001", "Something went wrong");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().code()).isEqualTo("E001");
        assertThat(response.getError().message()).isEqualTo("Something went wrong");
    }

    @Test
    void errorWithErrorCode_shouldUseErrorCodeValues() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.ENTITY_NOT_FOUND);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().code()).isEqualTo("C002");
        assertThat(response.getError().message()).isEqualTo("Entity not found");
    }

    @Test
    void successResponse_shouldSerializeToExpectedJsonStructure() throws Exception {
        ApiResponse<String> response = ApiResponse.success("hello");

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("data").asText()).isEqualTo("hello");
        assertThat(node.has("error")).isTrue();
        assertThat(node.get("error").isNull()).isTrue();
    }

    @Test
    void errorResponse_shouldSerializeToExpectedJsonStructure() throws Exception {
        ApiResponse<Void> response = ApiResponse.error("C001", "Invalid input");

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.has("data")).isTrue();
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.get("error").get("code").asText()).isEqualTo("C001");
        assertThat(node.get("error").get("message").asText()).isEqualTo("Invalid input");
    }
}
