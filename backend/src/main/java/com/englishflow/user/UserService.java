package com.englishflow.user;

import com.englishflow.user.dto.ChangePasswordRequest;
import com.englishflow.user.dto.DeleteAccountRequest;
import com.englishflow.user.dto.SettingsActionResponse;
import com.englishflow.user.dto.UpdatePreferencesRequest;
import com.englishflow.user.dto.UpdateProfileRequest;
import com.englishflow.user.dto.UpdateSettingsRequest;
import com.englishflow.user.dto.UserSettingsResponse;
import com.englishflow.user.exception.InvalidCurrentPasswordException;
import com.englishflow.user.exception.InvalidSettingsException;
import com.englishflow.user.exception.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Learner Profile & Settings Service.
 * Manages account settings, timezone, daily goals, audio playback speed,
 * secure password rotation, and destructive account erasure per DATA_LIFECYCLE.md.
 */
@Service
public class UserService {

    private static final Set<Double> ALLOWED_AUDIO_SPEEDS = Set.of(0.75, 1.0, 1.25);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getUserSettings(UUID userId) {
        User user = findUserById(userId);
        return UserSettingsResponse.fromUser(user);
    }

    @Transactional
    public UserSettingsResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserById(userId);
        if (request.displayName() == null) {
            throw new InvalidSettingsException("Display name must not be blank.");
        }
        String trimmedName = request.displayName().trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new InvalidSettingsException("Display name must be between 2 and 100 characters.");
        }
        user.setDisplayName(trimmedName);
        User saved = userRepository.save(user);
        return UserSettingsResponse.fromUser(saved);
    }

    @Transactional
    public UserSettingsResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        User user = findUserById(userId);

        if (request.timezone() != null) {
            user.setTimezone(validateTimezone(request.timezone()));
        }

        if (request.dailyGoalItems() != null) {
            validateDailyGoal(request.dailyGoalItems());
            user.setDailyGoalItems(request.dailyGoalItems());
        }

        if (request.preferredAudioSpeed() != null) {
            validateAudioSpeed(request.preferredAudioSpeed());
            user.setPreferredAudioSpeed(request.preferredAudioSpeed());
        }

        if (request.preferredLocale() != null) {
            user.setPreferredLocale(SupportedLocale.validate(request.preferredLocale()));
        }

        User saved = userRepository.save(user);
        return UserSettingsResponse.fromUser(saved);
    }

    @Transactional
    public UserSettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request) {
        User user = findUserById(userId);

        if (request.displayName() != null) {
            String trimmedName = request.displayName().trim();
            if (trimmedName.length() < 2 || trimmedName.length() > 100) {
                throw new InvalidSettingsException("Display name must be between 2 and 100 characters.");
            }
            user.setDisplayName(trimmedName);
        }

        if (request.timezone() != null) {
            user.setTimezone(validateTimezone(request.timezone()));
        }

        if (request.dailyGoalItems() != null) {
            validateDailyGoal(request.dailyGoalItems());
            user.setDailyGoalItems(request.dailyGoalItems());
        }

        if (request.preferredAudioSpeed() != null) {
            validateAudioSpeed(request.preferredAudioSpeed());
            user.setPreferredAudioSpeed(request.preferredAudioSpeed());
        }

        if (request.preferredLocale() != null) {
            user.setPreferredLocale(SupportedLocale.validate(request.preferredLocale()));
        }

        User saved = userRepository.save(user);
        return UserSettingsResponse.fromUser(saved);
    }

    @Transactional
    public SettingsActionResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        // Verify current password against database hash
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect.");
        }

        // Verify new password does not match current password
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidSettingsException("New password cannot be the same as the current password.");
        }

        // Verify confirmation if provided
        if (request.confirmPassword() != null && !request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidSettingsException("New password and confirmation password do not match.");
        }

        // Update password hash
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new SettingsActionResponse("SUCCESS", "Password changed successfully.");
    }

    @Transactional
    public SettingsActionResponse deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = findUserById(userId);

        if (request.confirmationText() != null && !request.confirmationText().isBlank()) {
            if (!"DELETE".equals(request.confirmationText().trim())) {
                throw new InvalidSettingsException("Confirmation text must be exactly DELETE.");
            }
        }

        // Re-authenticate with password before executing destructive hard delete
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Incorrect password. Account deletion aborted.");
        }

        // Hard delete app_users row (triggers PostgreSQL ON DELETE CASCADE on all dependent tables)
        userRepository.delete(user);
        userRepository.flush();

        return new SettingsActionResponse("DELETED", "Account successfully deleted.");
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new InvalidSettingsException("Timezone must not be blank.");
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception e) {
            throw new InvalidSettingsException("Invalid IANA timezone identifier: " + timezone);
        }
    }

    private void validateDailyGoal(int dailyGoal) {
        if (dailyGoal < 1 || dailyGoal > 500) {
            throw new InvalidSettingsException("Daily goal must be between 1 and 500 items.");
        }
    }

    private void validateAudioSpeed(Double speed) {
        if (speed == null || !ALLOWED_AUDIO_SPEEDS.contains(speed)) {
            throw new InvalidSettingsException("Audio playback speed must be one of: 0.75, 1.0, 1.25.");
        }
    }
}
