package com.forumx.answer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.request.UpdateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.mapper.AnswerMapper;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.answer.service.impl.AnswerServiceImpl;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.common.exception.AnswerNotFoundException;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.forumx.notification.service.NotificationApplicationService;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class AnswerServiceImplTest {

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AnswerMapper answerMapper;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private NotificationApplicationService notificationApplicationService;

    @InjectMocks
    private AnswerServiceImpl answerService;

    private Tenant tenant;
    private User author;
    private User otherUser;
    private Question question;
    private CustomUserDetails customUserDetails;
    private CustomUserDetails otherUserDetails;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).name("Test Tenant").slug("test").build();

        Role userRole = Role.builder().id(1L).roleName(RoleType.USER).active(true).build();
        UserRole userRoleRelation = UserRole.builder().user(author).role(userRole).active(true).build();

        author = User.builder()
                .id(2L)
                .username("author")
                .email("author@test.com")
                .tenant(tenant)
                .enabled(true)
                .userRoles(Set.of(userRoleRelation))
                .build();

        otherUser = User.builder()
                .id(3L)
                .username("other")
                .email("other@test.com")
                .tenant(tenant)
                .enabled(true)
                .userRoles(Set.of(userRoleRelation))
                .build();

        question = Question.builder()
                .id(4L)
                .title("Test Question Title")
                .content("Test Question Content")
                .tenant(tenant)
                .author(author)
                .answerCount(0)
                .deleted(false)
                .build();

        customUserDetails = new CustomUserDetails(author);
        otherUserDetails = new CustomUserDetails(otherUser);
    }

    @Test
    public void testCreateAnswerSuccess() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(tenantRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(tenant));
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(author));
        when(questionRepository.findByIdAndTenantIdAndDeletedFalse(4L, 1L)).thenReturn(Optional.of(question));

        CreateAnswerRequest request = new CreateAnswerRequest("This is a valid answer content.");
        Answer mappedAnswer = Answer.builder().content(request.getContent()).build();
        Answer savedAnswer = Answer.builder().id(5L).content(request.getContent()).author(author).question(question).tenant(tenant).build();
        AnswerResponse response = AnswerResponse.builder().id(5L).content(request.getContent()).authorId(2L).questionId(4L).build();

        when(answerMapper.toEntity(request)).thenReturn(mappedAnswer);
        when(answerRepository.save(mappedAnswer)).thenReturn(savedAnswer);
        when(answerMapper.toResponse(savedAnswer)).thenReturn(response);

        // Act
        AnswerResponse result = answerService.createAnswer(4L, request);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals(1, question.getAnswerCount()); // Incremented
        verify(questionRepository, times(1)).save(question);
        verify(answerRepository, times(1)).save(mappedAnswer);
        verifyNoInteractions(notificationApplicationService);
    }

    @Test
    public void testCreateAnswer_NotificationSent_WhenAnsweredByOtherUser() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(tenantRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(tenant));
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherUserDetails);
        when(userRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(otherUser));
        when(questionRepository.findByIdAndTenantIdAndDeletedFalse(4L, 1L)).thenReturn(Optional.of(question));

        CreateAnswerRequest request = new CreateAnswerRequest("This is an answer from another user.");
        Answer mappedAnswer = Answer.builder().content(request.getContent()).build();
        Answer savedAnswer = Answer.builder().id(5L).content(request.getContent()).author(otherUser).question(question).tenant(tenant).build();
        AnswerResponse response = AnswerResponse.builder().id(5L).content(request.getContent()).authorId(3L).questionId(4L).build();

        when(answerMapper.toEntity(request)).thenReturn(mappedAnswer);
        when(answerRepository.save(mappedAnswer)).thenReturn(savedAnswer);
        when(answerMapper.toResponse(savedAnswer)).thenReturn(response);

        // Act
        AnswerResponse result = answerService.createAnswer(4L, request);

        // Assert
        assertNotNull(result);
        verify(notificationApplicationService, times(1)).notifyAnswerCreated(
                eq(1L), // tenantId
                eq(author.getId()), // recipientId (question owner)
                eq(otherUser.getId()), // actorId (answerer)
                eq(otherUser.getUsername()), // actorUsername
                eq(4L) // questionId
        );
    }

    @Test
    public void testCreateAnswerQuestionNotFoundThrowsException() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(tenantRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(tenant));
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(author));
        when(questionRepository.findByIdAndTenantIdAndDeletedFalse(4L, 1L)).thenReturn(Optional.empty());

        CreateAnswerRequest request = new CreateAnswerRequest("This is a valid answer content.");

        // Act & Assert
        assertThrows(QuestionNotFoundException.class, () -> answerService.createAnswer(4L, request));
    }

    @Test
    public void testGetAnswersForQuestionSuccess() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);
        when(questionRepository.existsByIdAndTenantIdAndDeletedFalse(4L, 1L)).thenReturn(true);

        Pageable pageable = PageRequest.of(0, 10);
        Answer answer = Answer.builder().id(5L).content("Some answer content").build();
        Page<Answer> answersPage = new PageImpl<>(List.of(answer));

        when(answerRepository.findByQuestion_IdAndTenant_IdAndDeletedFalse(4L, 1L, pageable)).thenReturn(answersPage);

        AnswerResponse response = AnswerResponse.builder().id(5L).content("Some answer content").build();
        when(answerMapper.toResponse(answer)).thenReturn(response);

        // Act
        Page<AnswerResponse> result = answerService.getAnswersForQuestion(4L, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(5L, result.getContent().get(0).getId());
    }

    @Test
    public void testGetAnswerSuccess() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);

        Answer answer = Answer.builder().id(5L).content("Answer content").tenant(tenant).build();
        when(answerRepository.findByIdAndTenant_IdAndDeletedFalse(5L, 1L)).thenReturn(Optional.of(answer));

        AnswerResponse response = AnswerResponse.builder().id(5L).content("Answer content").build();
        when(answerMapper.toResponse(answer)).thenReturn(response);

        // Act
        AnswerResponse result = answerService.getAnswer(5L);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getId());
    }

    @Test
    public void testGetAnswerNotFoundThrowsException() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);
        when(answerRepository.findByIdAndTenant_IdAndDeletedFalse(5L, 1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AnswerNotFoundException.class, () -> answerService.getAnswer(5L));
    }

    @Test
    public void testUpdateAnswerSuccessByAuthor() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);

        Answer answer = Answer.builder().id(5L).content("Old content").author(author).tenant(tenant).build();
        when(answerRepository.findByIdAndTenant_IdAndDeletedFalse(5L, 1L)).thenReturn(Optional.of(answer));
        when(answerRepository.save(answer)).thenReturn(answer);

        UpdateAnswerRequest request = new UpdateAnswerRequest("New updated answer content.");
        AnswerResponse response = AnswerResponse.builder().id(5L).content(request.getContent()).build();
        when(answerMapper.toResponse(answer)).thenReturn(response);

        // Act
        AnswerResponse result = answerService.updateAnswer(5L, request);

        // Assert
        assertNotNull(result);
        assertEquals(request.getContent(), answer.getContent());
    }

    @Test
    public void testUpdateAnswerUnauthorizedThrowsException() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherUserDetails);

        Answer answer = Answer.builder().id(5L).content("Old content").author(author).tenant(tenant).build();
        when(answerRepository.findByIdAndTenant_IdAndDeletedFalse(5L, 1L)).thenReturn(Optional.of(answer));

        UpdateAnswerRequest request = new UpdateAnswerRequest("New updated answer content.");

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> answerService.updateAnswer(5L, request));
    }

    @Test
    public void testDeleteAnswerSuccessByAuthor() {
        // Arrange
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customUserDetails);

        question.setAnswerCount(1);
        Answer answer = Answer.builder().id(5L).content("Content").author(author).question(question).tenant(tenant).deleted(false).build();
        when(answerRepository.findByIdAndTenant_IdAndDeletedFalse(5L, 1L)).thenReturn(Optional.of(answer));

        // Act
        answerService.deleteAnswer(5L);

        // Assert
        assertTrue(answer.isDeleted());
        assertNotNull(answer.getDeletedAt());
        assertEquals(0, question.getAnswerCount()); // Decremented
        verify(questionRepository, times(1)).save(question);
        verify(answerRepository, times(1)).save(answer);
    }
}
