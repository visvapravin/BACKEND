package com.forumx.auth.entity;

import java.time.LocalDate;

import com.forumx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_profiles",
        indexes = {
                @Index(name = "idx_user_profiles_user_id", columnList = "user_id"),
                @Index(name = "idx_user_profiles_location", columnList = "location")
        }
)
public class UserProfile extends BaseEntity {

    // ── User association ────────────────────────────────────────────────

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_user_profiles_user"))
    private User user;

    // ── Name fields ─────────────────────────────────────────────────────

    // ── About ───────────────────────────────────────────────────────────

    @Size(max = 2000)
    @Column(length = 2000)
    private String bio;

    @Size(max = 500)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // ── Links ───────────────────────────────────────────────────────────

    @Size(max = 255)
    @Column(length = 255)
    private String website;

    @Size(max = 255)
    @Column(name = "github_url", length = 255)
    private String githubUrl;

    @Size(max = 255)
    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    // ── Professional ────────────────────────────────────────────────────

    @Size(max = 150)
    @Column(length = 150)
    private String location;

    // ── Regional ────────────────────────────────────────────────────────

    // ── Personal ────────────────────────────────────────────────────────

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    // ── Preferences ─────────────────────────────────────────────────────

    @Size(max = 10)
    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 10)
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_preference", nullable = false, length = 20)
    private NotificationPreference notificationPreference = NotificationPreference.ALL;

    // ── Enums ───────────────────────────────────────────────────────────

    public enum Gender {
        MALE,
        FEMALE,
        NON_BINARY,
        PREFER_NOT_TO_SAY
    }

    public enum ThemePreference {
        LIGHT,
        DARK,
        SYSTEM
    }

    public enum NotificationPreference {
        ALL,
        IMPORTANT_ONLY,
        NONE
    }
}
