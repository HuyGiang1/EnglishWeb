package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/dashboard", "/api/v1/analytics"})
public class DashboardController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public DashboardController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
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

    @GetMapping({"/summary", "/dashboard"})
    public ResponseEntity<DashboardSummaryResponse> getSummary(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        DashboardSummaryResponse response = analyticsService.getDashboardSummary(userId);
        return ResponseEntity.ok(response);
    }
}
