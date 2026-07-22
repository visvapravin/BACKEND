package com.forumx.notification.preference.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.entity.NotificationPreference;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.preference.dto.request.UpdateNotificationPreferenceRequest;
import com.forumx.notification.preference.dto.response.NotificationPreferenceResponse;
import com.forumx.notification.preference.service.NotificationPreferenceService;
import com.forumx.notification.repository.NotificationPreferenceRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getUserPreferences() {
        CurrentContext ctx = resolveCurrentContext();
        List<NotificationPreference> persisted = preferenceRepository
                .findAllByTenant_IdAndUser_IdAndDeletedFalse(ctx.tenantId(), ctx.userId());

        Map<NotificationType, NotificationPreference> map = persisted.stream()
                .collect(Collectors.toMap(NotificationPreference::getNotificationType, p -> p));

        return Arrays.stream(NotificationType.values())
                .map(type -> map.containsKey(type)
                        ? toResponse(map.get(type))
                        : createDefaultResponse(ctx.tenantId(), ctx.userId(), type))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getEffectivePreference(Long tenantId, Long userId, NotificationType notificationType) {
        if (tenantId == null || userId == null || notificationType == null) {
            return createDefaultResponse(tenantId, userId, notificationType);
        }

        Optional<NotificationPreference> opt = preferenceRepository
                .findByTenant_IdAndUser_IdAndNotificationTypeAndDeletedFalse(tenantId, userId, notificationType);

        return opt.map(this::toResponse)
                .orElseGet(() -> createDefaultResponse(tenantId, userId, notificationType));
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreference(UpdateNotificationPreferenceRequest request) {
        if (request == null || request.notificationType() == null) {
            throw new IllegalArgumentException("NotificationType is required to update preferences");
        }
        return updatePreferenceForType(request.notificationType(), request);
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreferenceForType(NotificationType type, UpdateNotificationPreferenceRequest request) {
        CurrentContext ctx = resolveCurrentContext();
        NotificationPreference pref = preferenceRepository
                .findByTenant_IdAndUser_IdAndNotificationTypeAndDeletedFalse(ctx.tenantId(), ctx.userId(), type)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findByIdAndDeletedFalse(ctx.tenantId())
                            .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + ctx.tenantId()));
                    User user = userRepository.findByIdAndDeletedFalse(ctx.userId())
                            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + ctx.userId()));
                    return NotificationPreference.builder()
                            .tenant(tenant)
                            .user(user)
                            .notificationType(type)
                            .emailEnabled(true)
                            .webSocketEnabled(true)
                            .pushEnabled(false)
                            .digestEnabled(false)
                            .build();
                });

        if (request.emailEnabled() != null) {
            pref.setEmailEnabled(request.emailEnabled());
        }
        if (request.webSocketEnabled() != null) {
            pref.setWebSocketEnabled(request.webSocketEnabled());
        }
        if (request.pushEnabled() != null) {
            pref.setPushEnabled(request.pushEnabled());
        }
        if (request.digestEnabled() != null) {
            pref.setDigestEnabled(request.digestEnabled());
        }

        NotificationPreference saved = preferenceRepository.save(pref);
        log.info("Updated notification preference. tenantId={}, userId={}, type={}, email={}, websocket={}",
                ctx.tenantId(), ctx.userId(), type, saved.isEmailEnabled(), saved.isWebSocketEnabled());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void resetUserPreferences() {
        CurrentContext ctx = resolveCurrentContext();
        List<NotificationPreference> persisted = preferenceRepository
                .findAllByTenant_IdAndUser_IdAndDeletedFalse(ctx.tenantId(), ctx.userId());
        for (NotificationPreference pref : persisted) {
            pref.setDeleted(true);
        }
        preferenceRepository.saveAll(persisted);
        log.info("Reset notification preferences to default for tenantId={}, userId={}", ctx.tenantId(), ctx.userId());
    }

    private CurrentContext resolveCurrentContext() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No active tenant context available");
        }
        CustomUserDetails userDetails = authenticationFacade.getCurrentUserDetails();
        if (userDetails == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return new CurrentContext(tenantId, userDetails.getUserId());
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.getId(),
                p.getTenant() != null ? p.getTenant().getId() : null,
                p.getUser() != null ? p.getUser().getId() : null,
                p.getNotificationType(),
                p.isEmailEnabled(),
                p.isWebSocketEnabled(),
                p.isPushEnabled(),
                p.isDigestEnabled()
        );
    }

    private NotificationPreferenceResponse createDefaultResponse(Long tenantId, Long userId, NotificationType type) {
        return new NotificationPreferenceResponse(
                null,
                tenantId,
                userId,
                type,
                true, // Default emailEnabled
                true, // Default webSocketEnabled
                false, // Default pushEnabled
                false  // Default digestEnabled
        );
    }

    private record CurrentContext(Long tenantId, Long userId) {}
}
