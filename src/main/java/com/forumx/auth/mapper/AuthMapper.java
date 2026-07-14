package com.forumx.auth.mapper;

import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper interface for user-related authentication and registration entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    /**
     * Maps user registration request parameters to a new {@link User} entity.
     *
     * @param request the registration details
     * @return the mapped User entity
     */
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "accountLocked", ignore = true)
    @Mapping(target = "accountExpired", ignore = true)
    @Mapping(target = "credentialsExpired", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "lastPasswordChangeAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "userProfile", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "googleId", ignore = true)
    User toUser(RegisterRequest request);

    /**
     * Maps basic User entity reference details to a new {@link UserProfile} entity.
     *
     * @param user the parent User entity
     * @return the base UserProfile entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "githubUrl", ignore = true)
    @Mapping(target = "linkedinUrl", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "timezone", ignore = true)
    @Mapping(target = "locale", ignore = true)
    @Mapping(target = "dateOfBirth", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "preferredLanguage", ignore = true)
    @Mapping(target = "themePreference", ignore = true)
    @Mapping(target = "notificationPreference", ignore = true)
    UserProfile toUserProfile(User user);

    /**
     * Maps {@link User} entity attributes to a base {@link LoginResponse} payload.
     *
     * @param user the authenticated User
     * @return base login response containing user info
     */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "displayName", source = "userProfile.displayName")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "tokenType", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    LoginResponse toLoginResponse(User user);
}
