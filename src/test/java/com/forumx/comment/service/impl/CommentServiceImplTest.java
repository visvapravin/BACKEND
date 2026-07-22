package com.forumx.comment.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import java.util.Collections;
import java.util.Optional;
import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.entity.Comment;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.comment.mapper.CommentMapper;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private NotificationApplicationService notificationApplicationService;

    @InjectMocks
    private CommentServiceImpl commentService;

    private Tenant tenant;
    private User author;
    private User otherUser;
    private Question question;
    private Answer answer;
    private CustomUserDetails authorDetails;
    private CustomUserDetails otherDetails;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).name("Tenant 1").slug("tenant1").build();

        author = User.builder()
                .id(10L)
                .username("author")
                .email("author@test.com")
                .tenant(tenant)
                .enabled(true)
                .build();
        Role userRole = Role.builder().id(1L).roleName(RoleType.USER).active(true).build();
        UserRole urole = UserRole.builder().user(author).role(userRole).active(true).build();
        author.setUserRoles(java.util.Set.of(urole));

        otherUser = User.builder()
                .id(20L)
                .username("other")
                .email("other@test.com")
                .tenant(tenant)
                .enabled(true)
                .build();
        UserRole otherUrole = UserRole.builder().user(otherUser).role(userRole).active(true).build();
        otherUser.setUserRoles(java.util.Set.of(otherUrole));

        question = Question.builder()
                .id(100L)
                .title("Question title")
                .author(author)
                .tenant(tenant)
                .build();

        answer = Answer.builder()
                .id(200L)
                .question(question)
                .author(author)
                .tenant(tenant)
                .build();

        authorDetails = new CustomUserDetails(author);
        otherDetails = new CustomUserDetails(otherUser);
    }

    @Test
    public void testCreateForQuestion_Success_SendsNotification() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherDetails);
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(otherUser));
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        CreateCommentRequest request = new CreateCommentRequest("Test comment for question");
        Comment savedComment = Comment.builder().id(500L).question(question).author(otherUser).content(request.getContent()).build();
        CommentResponse responseDto = CommentResponse.builder().id(500L).content(request.getContent()).authorId(20L).questionId(100L).build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(commentMapper.toResponse(savedComment)).thenReturn(responseDto);

        CommentResponse result = commentService.createForQuestion(100L, request);

        assertNotNull(result);
        assertEquals("Test comment for question", result.getContent());
        verify(notificationApplicationService, times(1)).notifyQuestionCommentCreated(
                eq(1L),
                eq(author.getId()),
                eq(otherUser.getId()),
                eq(otherUser.getUsername()),
                eq(100L)
        );
    }

    @Test
    public void testCreateForQuestion_SelfComment_NoNotification() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(authorDetails);
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(author));
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        CreateCommentRequest request = new CreateCommentRequest("Self comment");
        Comment savedComment = Comment.builder().id(501L).question(question).author(author).content(request.getContent()).build();
        CommentResponse responseDto = CommentResponse.builder().id(501L).content(request.getContent()).authorId(10L).questionId(100L).build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(commentMapper.toResponse(savedComment)).thenReturn(responseDto);

        CommentResponse result = commentService.createForQuestion(100L, request);

        assertNotNull(result);
        verifyNoInteractions(notificationApplicationService);
    }

    @Test
    public void testCreateForAnswer_Success_SendsNotification() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherDetails);
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(otherUser));
        when(answerRepository.findById(200L)).thenReturn(Optional.of(answer));

        CreateCommentRequest request = new CreateCommentRequest("Test comment for answer");
        Comment savedComment = Comment.builder().id(600L).answer(answer).author(otherUser).content(request.getContent()).build();
        CommentResponse responseDto = CommentResponse.builder().id(600L).content(request.getContent()).authorId(20L).answerId(200L).build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(commentMapper.toResponse(savedComment)).thenReturn(responseDto);

        CommentResponse result = commentService.createForAnswer(200L, request);

        assertNotNull(result);
        verify(notificationApplicationService, times(1)).notifyAnswerCommentCreated(
                eq(1L),
                eq(author.getId()),
                eq(otherUser.getId()),
                eq(otherUser.getUsername()),
                eq(200L)
        );
    }

    @Test
    public void testGetQuestionComments_EnforcesOldestFirst() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(authorDetails);
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(author));
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Comment> commentsPage = new PageImpl<>(Collections.emptyList());
        when(commentRepository.findByQuestionAndDeletedFalse(eq(question), any(Pageable.class))).thenReturn(commentsPage);

        commentService.getQuestionComments(100L, pageRequest);

        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByQuestionAndDeletedFalse(eq(question), pageableCaptor.capture());
        Pageable resolvedPageable = pageableCaptor.getValue();

        assertEquals(0, resolvedPageable.getPageNumber());
        assertEquals(10, resolvedPageable.getPageSize());
        Sort.Order order = resolvedPageable.getSort().getOrderFor("createdAt");
        assertNotNull(order);
        assertTrue(order.isAscending());
    }

    @Test
    public void testUpdateComment_AuthorSuccess() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(authorDetails);
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(author));

        Comment existing = Comment.builder().id(700L).author(author).content("Original content").build();
        when(commentRepository.findByIdAndDeletedFalse(700L)).thenReturn(Optional.of(existing));

        CreateCommentRequest request = new CreateCommentRequest("Updated content");
        Comment updated = Comment.builder().id(700L).author(author).content("Updated content").edited(true).build();
        when(commentRepository.save(existing)).thenReturn(updated);
        when(commentMapper.toResponse(updated)).thenReturn(CommentResponse.builder().id(700L).content("Updated content").edited(true).build());

        CommentResponse result = commentService.updateComment(700L, request);

        assertNotNull(result);
        assertTrue(result.isEdited());
        assertEquals("Updated content", result.getContent());
    }

    @Test
    public void testUpdateComment_UnauthorizedUser_ThrowsAccessDenied() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherDetails);
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(otherUser));

        Comment existing = Comment.builder().id(700L).author(author).content("Original content").build();
        when(commentRepository.findByIdAndDeletedFalse(700L)).thenReturn(Optional.of(existing));

        CreateCommentRequest request = new CreateCommentRequest("Trying to edit");
        assertThrows(AccessDeniedException.class, () -> commentService.updateComment(700L, request));
    }

    @Test
    public void testDeleteComment_ModeratorSuccess() {
        Tenant otherTenant = Tenant.builder().id(1L).build();
        User moderator = User.builder().id(30L).username("mod").tenant(otherTenant).build();
        Role modRole = Role.builder().id(2L).roleName(RoleType.MODERATOR).active(true).build();
        UserRole mrole = UserRole.builder().user(moderator).role(modRole).active(true).build();
        moderator.setUserRoles(java.util.Set.of(mrole));

        CustomUserDetails modDetails = new CustomUserDetails(moderator);

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(modDetails);
        when(userRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(moderator));

        Comment existing = Comment.builder().id(700L).author(author).content("To be deleted").build();
        when(commentRepository.findByIdAndDeletedFalse(700L)).thenReturn(Optional.of(existing));

        commentService.deleteComment(700L);

        assertTrue(existing.isDeleted());
        assertNotNull(existing.getDeletedAt());
        verify(commentRepository).save(existing);
    }
}
