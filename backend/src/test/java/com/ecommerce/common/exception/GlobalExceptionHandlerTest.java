package com.ecommerce.common.exception;

import com.ecommerce.common.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for GlobalExceptionHandler.
 * Verifies that each exception type maps to the correct HTTP status and ApiResponse structure.
 */
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessException_shouldReturnErrorCodeHttpStatus() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isConflict()) // 409 from DUPLICATE_ENTITY
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C006"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void entityNotFoundException_shouldReturn404() throws Exception {
        mockMvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"));
    }

    @Test
    void validationError_shouldReturn400WithFieldDetails() throws Exception {
        String requestBody = "{\"name\": \"\"}";
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    void unknownException_shouldReturn500() throws Exception {
        mockMvc.perform(get("/test/unknown-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"));
    }

    @Test
    void malformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    void methodNotAllowed_shouldReturn405() throws Exception {
        mockMvc.perform(post("/test/business-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C007"));
    }

    // Inner test controller that triggers each exception type
    @RestController
    static class TestController {

        @GetMapping("/test/business-exception")
        public void throwBusinessException() {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }

        @GetMapping("/test/entity-not-found")
        public void throwEntityNotFoundException() {
            throw new EntityNotFoundException("Product", 99L);
        }

        @PostMapping("/test/validation")
        public void throwValidationError(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unknown-exception")
        public void throwUnknownException() {
            throw new RuntimeException("Unexpected error");
        }

        record TestRequest(@NotBlank(message = "Name must not be blank") String name) {
        }
    }
}
