package com.forumx.comment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Collections;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.service.CommentService;
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
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommentService commentService;

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
    public void testCreateCommentForQuestion_Success() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("This is a valid comment");
        CommentResponse response = CommentResponse.builder()
                .id(100L)
                .content(request.getContent())
                .authorId(2L)
                .authorUsername("visva")
                .questionId(10L)
                .createdAt(Instant.now())
                .build();

        when(commentService.createForQuestion(eq(10L), any(CreateCommentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/questions/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.content").value("This is a valid comment"))
                .andExpect(jsonPath("$.authorUsername").value("visva"))
                .andExpect(jsonPath("$.questionId").value(10));
    }

    @Test
    public void testCreateCommentForQuestion_ValidationFailure_Blank() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(""); // blank content

        mockMvc.perform(post("/api/v1/questions/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateCommentForQuestion_ValidationFailure_TooShort() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("a"); // size 1 (min 2)

        mockMvc.perform(post("/api/v1/questions/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateCommentForAnswer_Success() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("Answer comment details");
        CommentResponse response = CommentResponse.builder()
                .id(101L)
                .content(request.getContent())
                .authorId(2L)
                .authorUsername("visva")
                .answerId(20L)
                .createdAt(Instant.now())
                .build();

        when(commentService.createForAnswer(eq(20L), any(CreateCommentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/answers/20/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.content").value("Answer comment details"))
                .andExpect(jsonPath("$.answerId").value(20));
    }

    @Test
    public void testGetQuestionComments_Success() throws Exception {
        Page<CommentResponse> comments = new PageImpl<>(Collections.singletonList(
                CommentResponse.builder().id(100L).content("Comment").questionId(10L).build()
        ));

        when(commentService.getQuestionComments(eq(10L), any(Pageable.class))).thenReturn(comments);

        mockMvc.perform(get("/api/v1/questions/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100));
    }

    @Test
    public void testUpdateComment_Success() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("Updated content");
        CommentResponse response = CommentResponse.builder().id(100L).content("Updated content").edited(true).build();

        when(commentService.updateComment(eq(100L), any(CreateCommentRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/comments/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.edited").value(true));
    }

    @Test
    public void testDeleteComment_Success() throws Exception {
        doNothing().when(commentService).deleteComment(100L);

        mockMvc.perform(delete("/api/v1/comments/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default"))
                .andExpect(status().isNoContent());
    }
}
