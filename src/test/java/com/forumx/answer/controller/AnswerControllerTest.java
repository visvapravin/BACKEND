package com.forumx.answer.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.request.UpdateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.service.AnswerService;
import com.forumx.auth.entity.User;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnswerService answerService;

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
    public void testCreateAnswerSuccess() throws Exception {
        CreateAnswerRequest request = new CreateAnswerRequest("This is a valid answer content body.");
        AnswerResponse response = AnswerResponse.builder()
                .id(100L)
                .questionId(10L)
                .authorId(2L)
                .authorUsername("visva")
                .content(request.getContent())
                .createdAt(Instant.now())
                .build();

        when(answerService.createAnswer(eq(10L), any(CreateAnswerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/questions/10/answers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.questionId").value(10))
                .andExpect(jsonPath("$.content").value("This is a valid answer content body."));
    }

    @Test
    public void testCreateAnswerValidationFailure() throws Exception {
        CreateAnswerRequest request = new CreateAnswerRequest("Too short"); // less than 10 characters

        mockMvc.perform(post("/api/v1/questions/10/answers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    public void testGetAnswersForQuestion() throws Exception {
        AnswerResponse response = AnswerResponse.builder()
                .id(100L)
                .questionId(10L)
                .authorId(2L)
                .authorUsername("visva")
                .content("Valid answer content body.")
                .createdAt(Instant.now())
                .build();

        Page<AnswerResponse> page = new PageImpl<>(List.of(response));

        when(answerService.getAnswersForQuestion(eq(10L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/questions/10/answers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].content").value("Valid answer content body."));
    }

    @Test
    public void testGetAnswerById() throws Exception {
        AnswerResponse response = AnswerResponse.builder()
                .id(100L)
                .questionId(10L)
                .authorId(2L)
                .authorUsername("visva")
                .content("Valid answer content body.")
                .createdAt(Instant.now())
                .build();

        when(answerService.getAnswer(100L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/answers/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.content").value("Valid answer content body."));
    }

    @Test
    public void testUpdateAnswer() throws Exception {
        UpdateAnswerRequest request = new UpdateAnswerRequest("Updated valid answer content body.");
        AnswerResponse response = AnswerResponse.builder()
                .id(100L)
                .questionId(10L)
                .authorId(2L)
                .authorUsername("visva")
                .content(request.getContent())
                .createdAt(Instant.now())
                .build();

        when(answerService.updateAnswer(eq(100L), any(UpdateAnswerRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/answers/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.content").value("Updated valid answer content body."));
    }

    @Test
    public void testDeleteAnswer() throws Exception {
        doNothing().when(answerService).deleteAnswer(100L);

        mockMvc.perform(delete("/api/v1/answers/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(answerService, times(1)).deleteAnswer(100L);
    }
}
