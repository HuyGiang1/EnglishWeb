package com.englishflow.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LocaleIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        SecurityContextHolder.clearContext();
        users.deleteAll();
    }

    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "locale-test"))
            .header("X-XSRF-TOKEN", "locale-test");
    }

    private MvcResult register(String email, String locale) throws Exception {
        var payload = new java.util.HashMap<String, Object>(Map.of(
            "email", email, "password", "LocaleTest123!", "displayName", "Locale Learner", "timezone", "UTC"));
        if (locale != null) payload.put("preferredLocale", locale);
        return mvc.perform(csrf(post("/api/v1/auth/register")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test void registrationStoresGuestSelectionAndExposesItInProfile() throws Exception {
        var result = register("english@example.com", "en");
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("preferredLocale").asText()).isEqualTo("en");
        assertThat(users.findByEmail("english@example.com").orElseThrow().getPreferredLocale()).isEqualTo("en");
        mvc.perform(get("/api/v1/auth/me").session(session(result)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value("en"))
            .andExpect(jsonPath("$.dailyGoalItems").value(20));
        mvc.perform(get("/api/v1/settings").session(session(result)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value("en"))
            .andExpect(jsonPath("$.preferredAudioSpeed").value(1.0));
    }

    @Test void legacyRegistrationAndLoginLeaveNullPreferenceWithoutWrites() throws Exception {
        register("legacy@example.com", null);
        var before = users.findByEmail("legacy@example.com").orElseThrow();
        mvc.perform(csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"legacy@example.com\",\"password\":\"LocaleTest123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").isEmpty());
        var after = users.findByEmail("legacy@example.com").orElseThrow();
        assertThat(after.getPreferredLocale()).isNull();
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test void preferenceUpdatePersistsBothSupportedValues() throws Exception {
        var session = session(register("update@example.com", null));
        for (String locale : new String[]{"vi-VN", "en"}) {
            mvc.perform(csrf(put("/api/v1/settings/preferences")).session(session).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("preferredLocale", locale))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value(locale));
            assertThat(users.findByEmail("update@example.com").orElseThrow().getPreferredLocale()).isEqualTo(locale);
        }
    }

    @Test void invalidUpdateRollsBackOtherFields() throws Exception {
        var session = session(register("invalid@example.com", "en"));
        for (String locale : new String[]{"fr", "en-US", "", "EN", " en "}) {
            mvc.perform(csrf(put("/api/v1/settings/preferences")).session(session).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("preferredLocale", locale, "dailyGoalItems", 35))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        var user = users.findByEmail("invalid@example.com").orElseThrow();
        assertThat(user.getPreferredLocale()).isEqualTo("en");
        assertThat(user.getDailyGoalItems()).isEqualTo(20);
    }

    @Test void invalidRegistrationDoesNotCreateAccount() throws Exception {
        mvc.perform(csrf(post("/api/v1/auth/register")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad@example.com\",\"password\":\"LocaleTest123!\",\"displayName\":\"Learner\",\"preferredLocale\":\"fr\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(users.findByEmail("bad@example.com")).isEmpty();
    }

    @Test void anonymousUpdateWithValidCsrfIsRejected() throws Exception {
        mvc.perform(csrf(put("/api/v1/settings/preferences")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLocale\":\"en\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void authenticatedUpdateWithoutCsrfIsRejected() throws Exception {
        var session = session(register("csrf@example.com", "vi-VN"));
        mvc.perform(put("/api/v1/settings/preferences").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLocale\":\"en\"}"))
            .andExpect(status().isForbidden());
        assertThat(users.findByEmail("csrf@example.com").orElseThrow().getPreferredLocale()).isEqualTo("vi-VN");
    }

    @Test void clientSuppliedUserIdCannotChangeAnotherAccount() throws Exception {
        var own = session(register("own@example.com", "vi-VN"));
        register("other@example.com", "vi-VN");
        var otherId = users.findByEmail("other@example.com").orElseThrow().getId();
        mvc.perform(csrf(put("/api/v1/settings/preferences")).session(own).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("userId", otherId, "preferredLocale", "en"))))
            .andExpect(status().isOk());
        assertThat(users.findByEmail("own@example.com").orElseThrow().getPreferredLocale()).isEqualTo("en");
        assertThat(users.findById(otherId).orElseThrow().getPreferredLocale()).isEqualTo("vi-VN");
    }

    @Test void oldSettingsPayloadPreservesLocaleAndUnifiedUpdateSupportsLocale() throws Exception {
        var session = session(register("compat@example.com", "en"));
        mvc.perform(csrf(put("/api/v1/settings")).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyGoalItems\":30}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value("en"));
        mvc.perform(csrf(put("/api/v1/settings")).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLocale\":\"vi-VN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value("vi-VN"));
    }

    @Test void preferenceSurvivesAnotherLoginAndLogout() throws Exception {
        var session = session(register("device@example.com", "en"));
        mvc.perform(csrf(post("/api/v1/auth/logout")).session(session)).andExpect(status().isNoContent());
        mvc.perform(csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"device@example.com\",\"password\":\"LocaleTest123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredLocale").value("en"));
    }

    @Test void databaseConstraintRejectsUnsupportedLocale() throws Exception {
        var id = users.findByEmail("db@example.com");
        register("db@example.com", "en");
        assertThat(id).isEmpty();
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET preferred_locale='fr' WHERE email='db@example.com'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void upgradeFromV10PreservesLegacyRowsAndHasNoDefault() throws Exception {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("locale_upgrade").defaultSchema("locale_upgrade").locations("classpath:db/migration");
        config.target("10").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO locale_upgrade.app_users(email,password_hash,display_name) VALUES ('legacy-migration@example.com','fixture-only','Legacy')");
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("locale_upgrade").defaultSchema("locale_upgrade").locations("classpath:db/migration").load().migrate();
            try (var rows = statement.executeQuery("SELECT preferred_locale,display_name FROM locale_upgrade.app_users")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
                assertThat(rows.getString(2)).isEqualTo("Legacy");
            }
            try (var rows = statement.executeQuery("SELECT is_nullable,column_default FROM information_schema.columns WHERE table_schema='locale_upgrade' AND table_name='app_users' AND column_name='preferred_locale'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("YES");
                assertThat(rows.getString(2)).isNull();
            }
        }
    }
}
