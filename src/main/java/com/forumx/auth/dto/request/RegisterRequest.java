package com.forumx.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    // ── Tenant ──────────────────────────────────────────────────────────

    @NotBlank(message = "Tenant slug is required")
    @Size(max = 100, message = "Tenant slug must not exceed 100 characters")
    private String tenantSlug;

    // ── Identity ────────────────────────────────────────────────────────

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, hyphens, and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    // ── Profile ─────────────────────────────────────────────────────────

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits and may start with +")
    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    private String phoneNumber;

    // ── Professional (stored in UserProfile) ────────────────────────────

    @Size(max = 150, message = "Company must not exceed 150 characters")
    private String company;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;

    @Size(max = 100, message = "Job title must not exceed 100 characters")
    private String jobTitle;

    // ── Regional (stored in UserProfile) ────────────────────────────────

    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    private String timezone;

    @Size(max = 10, message = "Locale must not exceed 10 characters")
    private String locale;
}
