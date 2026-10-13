package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.analytics.dto.HeatmapResponse;
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

    /**
     * Dashboard Summary Metrics.
     * Canonical Route: GET /api/v1/dashboard/summary
     * Supported Alias: GET /api/v1/analytics/dashboard
     */
    @GetMapping({"/api/v1/dashboard/summary", "/api/v1/analytics/dashboard"})
    public ResponseEntity<DashboardSummaryResponse> getSummary(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        DashboardSummaryResponse response = analyticsService.getDashboardSummary(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 52-Week Learning Activity Heatmap.
     * Canonical Route: GET /api/v1/analytics/activity-heatmap
     * Supported Aliases:
     *   - GET /api/v1/dashboard/activity-heatmap
     *   - GET /api/v1/dashboard/heatmap
     */
    @GetMapping({"/api/v1/analytics/activity-heatmap", "/api/v1/dashboard/activity-heatmap", "/api/v1/dashboard/heatmap"})
    public ResponseEntity<HeatmapResponse> getActivityHeatmap(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        HeatmapResponse response = analyticsService.getActivityHeatmap(userId);
        return ResponseEntity.ok(response);
    }
}
