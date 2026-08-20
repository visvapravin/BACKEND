package com.forumx.auth.invitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.time.Instant;

import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.invitation.service.InvitationService;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AccountScopeValidator;
import com.forumx.notification.publisher.NotificationPublisher;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class InvitationServiceScopeTest {

    @Test
    void rejectsTenantlessModeratorBeforeAnyOnboardingPersistence() {
        ModeratorInvitationRepository invitations = mock(ModeratorInvitationRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserRoleRepository userRoles = mock(UserRoleRepository.class);
        ModeratorInvitation invitation = ModeratorInvitation.builder()
                .email("moderator@example.com")
                .role(com.forumx.auth.enums.RoleType.MODERATOR)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .tokenHash("hash")
                .build();
        when(invitations.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(invitation));

        InvitationService service = new InvitationService(
                invitations, users, mock(RoleRepository.class), userRoles,
                mock(UserProfileRepository.class), mock(com.forumx.tenant.repository.TenantRepository.class),
                mock(TenantResolver.class), mock(AuthenticationFacade.class),
                mock(PasswordEncoder.class), mock(NotificationPublisher.class),
                new AccountScopeValidator());

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("token").username("moderator").password("password123")
                .confirmPassword("password123").build();

        assertThrows(IllegalArgumentException.class, () -> service.acceptInvitation(request));
        verify(users, never()).save(any());
        verify(userRoles, never()).save(any());
        verify(invitations, never()).save(any());
        verify(invitations).findByTokenHashForUpdate(anyString());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
    }
}
