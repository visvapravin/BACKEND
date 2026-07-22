package com.forumx.support.ticket.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.auth.entity.User;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import com.forumx.support.ticket.service.TicketMessageService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class TicketMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketMessageService ticketMessageService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private TenantResolver tenantResolver;

    private User mockUser;
    private CustomUserDetails customUserDetails;
    private String validToken;

    @BeforeEach
    public void setUp() {
        Tenant tenant = Tenant.builder()
                .id(1L)
                .name("Default Tenant")
                .slug("default")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        mockUser = User.builder()
                .id(2L)
                .username("visva")
                .email("visva@test.com")
                .passwordHash("hashedPassword")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();

        customUserDetails = new CustomUserDetails(mockUser);

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(userDetailsService.loadUserByUsername("visva")).thenReturn(customUserDetails);

        validToken = jwtTokenProvider.generateAccessToken(customUserDetails);
    }

    @Test
    public void testSendMessageSuccess() throws Exception {
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("Hello, this is a test message.");
        
        TicketMessageResponse response = TicketMessageResponse.builder()
                .id(1001L)
                .ticketId(100L)
                .senderId(2L)
                .senderName("visva")
                .message(request.getMessage())
                .messageUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(ticketMessageService.sendMessage(eq(100L), any(CreateTicketMessageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/support/tickets/100/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1001L))
                .andExpect(jsonPath("$.message").value("Hello, this is a test message."))
                .andExpect(jsonPath("$.senderName").value("visva"))
                .andExpect(jsonPath("$.messageUuid").exists());
    }

    @Test
    public void testSendMessage_ValidationFailure() throws Exception {
        // Message too short (min = 2)
        CreateTicketMessageRequest shortRequest = new CreateTicketMessageRequest("A");

        mockMvc.perform(post("/api/v1/support/tickets/100/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.validationErrors.message").exists());
    }

    @Test
    public void testGetConversationSuccess() throws Exception {
        TicketMessageResponse response1 = TicketMessageResponse.builder()
                .id(1001L)
                .ticketId(100L)
                .senderId(2L)
                .senderName("visva")
                .message("Message 1")
                .createdAt(Instant.now())
                .build();

        List<TicketMessageResponse> responseList = Collections.singletonList(response1);
        PageImpl<TicketMessageResponse> page = new PageImpl<>(responseList);

        when(ticketMessageService.getConversation(eq(100L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/support/tickets/100/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1001L))
                .andExpect(jsonPath("$.content[0].message").value("Message 1"))
                .andExpect(jsonPath("$.content[0].senderName").value("visva"));
    }
}
