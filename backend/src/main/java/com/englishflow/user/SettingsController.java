package com.englishflow.user;

import com.englishflow.user.dto.ChangePasswordRequest;
import com.englishflow.user.dto.DeleteAccountRequest;
import com.englishflow.user.dto.SettingsActionResponse;
import com.englishflow.user.dto.UpdatePreferencesRequest;
import com.englishflow.user.dto.UpdateProfileRequest;
import com.englishflow.user.dto.UpdateSettingsRequest;
import com.englishflow.user.dto.UserSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for User Profile & Settings.
 * Implements endpoints per docs/api/API_CONTRACT.md Section 10 and docs/api/API_COVERAGE.md Screen 13.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final UserService userService;
    private final UserRepository userRepository;

    public SettingsController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BadCredentialsException("Unauthenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElseThrow(() -> new BadCredentialsException("User not found: " + email));
    }

    /**
     * Read User Settings.
     * Route: GET /api/v1/settings
     */
    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(userService.getUserSettings(userId));
    }

    /**
     * Unified Settings Update.
     * Route: PUT /api/v1/settings
     */
    @PutMapping
    public ResponseEntity<UserSettingsResponse> updateSettings(
        @RequestBody UpdateSettingsRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(userService.updateSettings(userId, request));
    }

    /**
     * Profile Update (Display Name).
     * Route: PUT /api/v1/settings/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<UserSettingsResponse> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /**
     * Preferences Update (Timezone, Daily Goal, Audio Speed).
     * Route: PUT /api/v1/settings/preferences
     */
    @PutMapping("/preferences")
    public ResponseEntity<UserSettingsResponse> updatePreferences(
        @RequestBody UpdatePreferencesRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(userService.updatePreferences(userId, request));
    }

    /**
     * Secure Password Change (PUT).
     * Route: PUT /api/v1/settings/password
     */
    @PutMapping("/password")
    public ResponseEntity<SettingsActionResponse> changePasswordPut(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        SettingsActionResponse response = userService.changePassword(userId, request);
        // Rotate session ID post-commit to eliminate session fixation while preserving authenticated session
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                httpRequest.changeSessionId();
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Secure Password Change Alias (POST).
     * Route: POST /api/v1/settings/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<SettingsActionResponse> changePasswordPost(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        SettingsActionResponse response = userService.changePassword(userId, request);
        // Rotate session ID post-commit to eliminate session fixation while preserving authenticated session
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                httpRequest.changeSessionId();
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Account Deletion / Data Erasure.
     * Route: DELETE /api/v1/settings/account
     */
    @DeleteMapping("/account")
    public ResponseEntity<SettingsActionResponse> deleteAccount(
        @Valid @RequestBody DeleteAccountRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        // Database deletion executes within the @Transactional service boundary
        SettingsActionResponse response = userService.deleteAccount(userId, request);

        // ONLY after database commit succeeds without exception, invalidate session and clear security context
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        SecurityContextHolder.clearContext();

        // Expire JSESSIONID cookie
        if (httpResponse != null) {
            ResponseCookie cookie = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return ResponseEntity.ok(response);
    }
}
