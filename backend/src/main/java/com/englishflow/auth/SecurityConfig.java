package com.englishflow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Spring Security Configuration for EnglishFlow.
 * Enforces session-based authentication (JSESSIONID cookie),
 * synchronized CSRF protection (XSRF-TOKEN cookie & X-XSRF-TOKEN header),
 * and strict authorization boundaries per API_CONTRACT.md.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");

        http
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.migrateSession())
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(tokenRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers("/actuator/health", "/actuator/info")
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/media/audio/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/v1/media/audio/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    com.englishflow.shared.error.ApiErrorResponse error = com.englishflow.shared.error.ApiErrorResponse.of(
                        "UNAUTHORIZED",
                        "Full authentication is required to access this resource.",
                        HttpStatus.UNAUTHORIZED.value()
                    );
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .writeValue(response.getOutputStream(), error);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    com.englishflow.shared.error.ApiErrorResponse error = com.englishflow.shared.error.ApiErrorResponse.of(
                        "FORBIDDEN",
                        "Access denied or CSRF verification failed.",
                        HttpStatus.FORBIDDEN.value()
                    );
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .writeValue(response.getOutputStream(), error);
                })
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * SPA-specific CSRF Request Handler that unwraps raw unmasked tokens sent in header.
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (headerValue != null && !headerValue.isBlank()) ? headerValue : this.delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /**
     * Filter that ensures deferred CSRF tokens are loaded and the XSRF-TOKEN cookie is set on responses.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            Object tokenAttr = request.getAttribute(CsrfToken.class.getName());
            if (tokenAttr instanceof CsrfToken csrfToken) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
