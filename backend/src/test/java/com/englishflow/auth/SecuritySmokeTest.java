package com.englishflow.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecuritySmokeTest.TestEndpointConfig.class)
@Testcontainers
class SecuritySmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestEndpointConfig {
        @RestController
        static class TestEndpointController {
            @GetMapping("/api/v1/test-protected")
            public String getProtected() {
                return "ok";
            }

            @PostMapping("/api/v1/test-protected")
            public String postProtected() {
                return "created";
            }
        }
    }

    @Test
    @DisplayName("Health endpoint /actuator/health is publicly accessible without authentication")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Unauthenticated request to protected route returns 401 Unauthorized")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/test-protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated state-changing POST request without CSRF token is rejected with 403 Forbidden by CsrfFilter")
    void unauthenticatedPostWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/test-protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated POST request with CSRF token is rejected with 401 Unauthorized by AuthorizationFilter")
    void unauthenticatedPostWithCsrfIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/test-protected").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated POST request missing CSRF token is rejected with 403 Forbidden")
    void authenticatedPostWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/test-protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated POST request with valid CSRF token succeeds with 200 OK")
    void authenticatedPostWithCsrfSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/test-protected").with(csrf()))
                .andExpect(status().isOk());
    }
}
