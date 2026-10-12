package com.englishflow.auth;

import com.englishflow.auth.dto.LoginRequest;
import com.englishflow.auth.dto.RegisterRequest;
import com.englishflow.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Bootstrap endpoint to issue or refresh XSRF-TOKEN cookie for browser clients.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        Object csrfObj = request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (csrfObj instanceof org.springframework.security.web.csrf.CsrfToken csrfToken) {
            csrfToken.getToken();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        UserResponse response = authService.register(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        UserResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        UserResponse response = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
    }
}
