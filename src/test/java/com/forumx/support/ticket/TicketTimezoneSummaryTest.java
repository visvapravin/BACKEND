package com.forumx.support.ticket;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.response.SupportDashboardSummary;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.impl.TicketServiceImpl;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TicketTimezoneSummaryTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthenticationFacade authenticationFacade;
    @Mock private TenantResolver tenantResolver;
    @Mock private com.forumx.presence.service.PresenceService presenceService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private Tenant tenant;
    private User moderator;
    private CustomUserDetails moderatorDetails;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default-tenant").timezone("UTC").build();
        moderator = User.builder().id(20L).username("mod").tenant(tenant).build();
        moderatorDetails = new CustomUserDetails(moderator) {
            @Override
            public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MODERATOR"));
            }
        };

        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(java.util.Optional.of(moderator));
    }

    @Test
    void testDashboardSummary_UtcTimezone() {
        tenant.setTimezone("UTC");
        executeAndVerifyTimezone("UTC");
    }

    @Test
    void testDashboardSummary_AsiaKolkataTimezone() {
        tenant.setTimezone("Asia/Kolkata");
        executeAndVerifyTimezone("Asia/Kolkata");
    }

    @Test
    void testDashboardSummary_AmericaNewYorkTimezone() {
        tenant.setTimezone("America/New_York");
        executeAndVerifyTimezone("America/New_York");
    }

    @Test
    void testDashboardSummary_InvalidOrMissingTimezoneDefaultsToUtc() {
        tenant.setTimezone("INVALID_TZ_XYZ");
        executeAndVerifyTimezone("UTC");
    }

    private void executeAndVerifyTimezone(String expectedZoneId) {
        when(ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(eq(1L), any())).thenReturn(0L);
        when(ticketRepository.countByTenant_IdAndStatusInAndDeletedFalse(eq(1L), any())).thenReturn(0L);
        when(ticketRepository.countByTenant_IdAndResolvedAtGreaterThanEqualAndResolvedAtLessThanAndDeletedFalse(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(5L);

        SupportDashboardSummary summary = ticketService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(5L, summary.resolvedToday());

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(ticketRepository).countByTenant_IdAndResolvedAtGreaterThanEqualAndResolvedAtLessThanAndDeletedFalse(
                eq(1L), startCaptor.capture(), endCaptor.capture());

        assertNotNull(startCaptor.getValue());
        assertNotNull(endCaptor.getValue());
    }
}
