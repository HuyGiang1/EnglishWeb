package com.englishflow.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User Identity & Account Entity.
 * Maps to canonical table 'app_users' per docs/architecture/DATABASE.md Section 3.1.
 */
@Entity
@Table(name = "app_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "daily_goal_items", nullable = false)
    private int dailyGoalItems = 20;

    @Column(name = "preferred_audio_speed", nullable = false)
    private Double preferredAudioSpeed = 1.0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {
    }

    public User(String email, String passwordHash, String displayName, String timezone, int dailyGoalItems) {
        this(email, passwordHash, displayName, timezone, dailyGoalItems, 1.0);
    }

    public User(String email, String passwordHash, String displayName, String timezone, int dailyGoalItems, Double preferredAudioSpeed) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timezone = (timezone != null && !timezone.isBlank()) ? timezone : "UTC";
        this.dailyGoalItems = dailyGoalItems > 0 ? dailyGoalItems : 20;
        this.preferredAudioSpeed = (preferredAudioSpeed != null) ? preferredAudioSpeed : 1.0;
        this.isActive = true;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getDailyGoalItems() {
        return dailyGoalItems;
    }

    public void setDailyGoalItems(int dailyGoalItems) {
        this.dailyGoalItems = dailyGoalItems;
    }

    public Double getPreferredAudioSpeed() {
        return preferredAudioSpeed;
    }

    public void setPreferredAudioSpeed(Double preferredAudioSpeed) {
        this.preferredAudioSpeed = preferredAudioSpeed;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
