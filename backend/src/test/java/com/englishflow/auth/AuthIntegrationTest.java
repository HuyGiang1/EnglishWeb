package com.englishflow.auth;

import com.englishflow.auth.dto.LoginRequest;
import com.englishflow.auth.dto.RegisterRequest;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    private static final String MOCK_CSRF = "b1c2d3e4-valid-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder
            .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
            .header("X-XSRF-TOKEN", MOCK_CSRF);
    }

    @Test
    @DisplayName("GET /api/v1/auth/csrf returns 204 and populates XSRF-TOKEN cookie")
    void bootstrapCsrfTokenSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(setCookie != null || cookie != null).isTrue();
        if (setCookie != null) {
            assertThat(setCookie).contains("XSRF-TOKEN");
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth/register without CSRF token is rejected with 403 Forbidden")
    void registerWithoutCsrfReturnsForbidden() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "SecurePassword123!",
            "Test User",
            "UTC"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with invalid data returns 400 Bad Request with redacted secrets")
    void registerWithInvalidDataReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "not-an-email",
            "weak",
            "",
            "UTC"
        );

        mockMvc.perform(withCsrf(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.validationErrors").isArray())
            .andExpect(jsonPath("$.validationErrors[?(@.field == 'password')].rejectedValue").value(org.hamcrest.Matchers.hasItem("[REDACTED]")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register persists user, stores BCrypt hash, and auto-establishes session")
    void registerSuccessPersistsUserAndEstablishesSession() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "learner@example.com",
            "SecurePassword123!",
            "Minh Tran",
            "Asia/Ho_Chi_Minh"
        );

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.email").value("learner@example.com"))
            .andExpect(jsonPath("$.displayName").value("Minh Tran"))
            .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
            .andExpect(jsonPath("$.dailyGoalItems").value(20))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist())
            .andReturn();

        // Verify user in PostgreSQL database
        Optional<User> persisted = userRepository.findByEmail("learner@example.com");
        assertThat(persisted).isPresent();
        User user = persisted.get();
        assertThat(user.getDisplayName()).isEqualTo("Minh Tran");
        assertThat(user.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(user.getPasswordHash()).startsWith("$2a$");
        assertThat(user.getPasswordHash()).isNotEqualTo("SecurePassword123!");
        assertThat(passwordEncoder.matches("SecurePassword123!", user.getPasswordHash())).isTrue();

        // Verify session was established
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Verify /api/v1/auth/me works with this session
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("learner@example.com"))
            .andExpect(jsonPath("$.displayName").value("Minh Tran"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register rejects duplicate normalized email with 409 Conflict")
    void registerDuplicateEmailReturnsConflict() throws Exception {
        RegisterRequest request1 = new RegisterRequest(
            "student@example.com",
            "SecurePassword123!",
            "Student One",
            "UTC"
        );

        mockMvc.perform(withCsrf(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        // Attempt registering with uppercase variant of identical email
        RegisterRequest request2 = new RegisterRequest(
            "STUDENT@EXAMPLE.COM",
            "SecurePassword123!",
            "Student Two",
            "UTC"
        );

        mockMvc.perform(withCsrf(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login succeeds with valid credentials and rejects invalid credentials")
    void loginWorkflowVerification() throws Exception {
        // Pre-create user in database
        User user = new User(
            "loginuser@example.com",
            passwordEncoder.encode("SecurePassword123!"),
            "Login User",
            "UTC",
            20
        );
        userRepository.save(user);

        // 1. Invalid password
        LoginRequest wrongPassword = new LoginRequest("loginuser@example.com", "WrongPassword123!");
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPassword)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 2. Non-existent email
        LoginRequest nonExistent = new LoginRequest("unknown@example.com", "SecurePassword123!");
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonExistent)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 3. Valid credentials
        LoginRequest validLogin = new LoginRequest("loginuser@example.com", "SecurePassword123!");
        MvcResult loginResult = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLogin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("loginuser@example.com"))
            .andExpect(jsonPath("$.displayName").value("Login User"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // 4. Verify /api/v1/auth/me returns authenticated profile
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("loginuser@example.com"));

        // 5. Logout invalidates session
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout"))
                .session(session))
            .andExpect(status().isNoContent());

        // 6. Post-logout /api/v1/auth/me returns 401
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me without session returns 401 Unauthorized")
    void unauthenticatedCurrentUserReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Focused regression: multi-step login, protected POST, logout, re-login, and expired session handling")
    void fullSessionLifecycleRegression() throws Exception {
        // Setup user
        User user = new User(
            "cycle@example.com",
            passwordEncoder.encode("SecurePass123!"),
            "Cycle User",
            "Asia/Bangkok",
            25
        );
        userRepository.save(user);

        // 1. CSRF token bootstrap before login
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();
        String csrfCookie = csrfResult.getResponse().getHeader("Set-Cookie");
        assertThat(csrfCookie).isNotNull();

        // 2. Login creates authenticated session
        LoginRequest loginRequest = new LoginRequest("cycle@example.com", "SecurePass123!");
        MvcResult loginResult = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("cycle@example.com"))
            .andReturn();

        MockHttpSession session1 = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session1).isNotNull();

        // 3. Protected GET /auth/me immediately after login
        mockMvc.perform(get("/api/v1/auth/me").session(session1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Cycle User"));

        // 4. Logout invalidation with CSRF
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")).session(session1))
            .andExpect(status().isNoContent());

        // 5. Subsequent request on invalidated session returns 401
        mockMvc.perform(get("/api/v1/auth/me").session(session1))
            .andExpect(status().isUnauthorized());

        // 6. Login again in new session succeeds
        MvcResult reloginResult = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("cycle@example.com"))
            .andReturn();

        MockHttpSession session2 = (MockHttpSession) reloginResult.getRequest().getSession(false);
        assertThat(session2).isNotNull();
        assertThat(session2.getId()).isNotEqualTo(session1.getId());

        // 7. Simulated expired/invalid session cookie returns 401 safely
        MockHttpSession expiredSession = new MockHttpSession();
        expiredSession.invalidate();
        mockMvc.perform(get("/api/v1/auth/me").session(expiredSession))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
