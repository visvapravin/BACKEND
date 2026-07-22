package com.forumx.bookmark.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.entity.Bookmark;
import com.forumx.bookmark.mapper.BookmarkMapper;
import com.forumx.bookmark.repository.BookmarkRepository;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class BookmarkServiceImplTest {

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private AuthenticationFacade authenticationFacade;
    @Mock private TenantResolver tenantResolver;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    private Tenant tenant1;
    private Tenant tenant2;
    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    public void setUp() {
        tenant1 = Tenant.builder().id(1L).name("T1").build();
        tenant2 = Tenant.builder().id(2L).name("T2").build();
        user = User.builder().id(10L).username("buser").tenant(tenant1).enabled(true).build();
        userDetails = new CustomUserDetails(user);
    }

    private void mockAuth() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(userDetails);
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(user));
    }

    @Test
    public void testToggleBookmark_CreateNew() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant1).deleted(false).title("Q").build();

        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(bookmarkRepository.findByUserAndQuestion(user, question)).thenReturn(Optional.empty());
        when(tenantRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(tenant1));

        Bookmark saved = Bookmark.builder().id(200L).question(question).user(user).build();
        when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(saved);

        BookmarkResponse responseDto = new BookmarkResponse(100L, "Q", Instant.now());
        when(bookmarkMapper.toResponse(saved)).thenReturn(responseDto);

        BookmarkResponse result = bookmarkService.toggleBookmark(100L);

        assertNotNull(result);
        assertEquals(100L, result.getQuestionId());
        verify(bookmarkRepository, times(1)).save(any(Bookmark.class));
    }

    @Test
    public void testToggleBookmark_SoftDeleteActive() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant1).deleted(false).title("Q").build();
        Bookmark active = Bookmark.builder().id(200L).question(question).user(user).deleted(false).build();

        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(bookmarkRepository.findByUserAndQuestion(user, question)).thenReturn(Optional.of(active));
        when(bookmarkRepository.save(active)).thenReturn(active);

        bookmarkService.toggleBookmark(100L);

        assertTrue(active.isDeleted());
        assertNotNull(active.getDeletedAt());
        verify(bookmarkRepository, times(1)).save(active);
    }

    @Test
    public void testToggleBookmark_RestoreDeleted() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant1).deleted(false).title("Q").build();
        Bookmark softDeleted = Bookmark.builder().id(200L).question(question).user(user).deleted(true).deletedAt(Instant.now()).build();

        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(bookmarkRepository.findByUserAndQuestion(user, question)).thenReturn(Optional.of(softDeleted));
        when(bookmarkRepository.save(softDeleted)).thenReturn(softDeleted);

        bookmarkService.toggleBookmark(100L);

        assertFalse(softDeleted.isDeleted());
        assertNull(softDeleted.getDeletedAt());
        verify(bookmarkRepository, times(1)).save(softDeleted);
    }

    @Test
    public void testToggleBookmark_DeletedQuestion_ThrowsException() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant1).deleted(true).build();
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThrows(QuestionNotFoundException.class, () -> {
            bookmarkService.toggleBookmark(100L);
        });
    }

    @Test
    public void testToggleBookmark_CrossTenant_ThrowsException() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant2).deleted(false).build();
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThrows(AccessDeniedException.class, () -> {
            bookmarkService.toggleBookmark(100L);
        });
    }

    @Test
    public void testRemoveBookmark_Success() {
        mockAuth();
        Bookmark bookmark = Bookmark.builder().id(200L).question(Question.builder().id(100L).build()).user(user).deleted(false).build();
        when(bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(user, 100L)).thenReturn(Optional.of(bookmark));
        when(bookmarkRepository.save(bookmark)).thenReturn(bookmark);

        bookmarkService.removeBookmark(100L);

        assertTrue(bookmark.isDeleted());
        verify(bookmarkRepository, times(1)).save(bookmark);
    }

    @Test
    public void testRemoveBookmark_NotFound_ThrowsException() {
        mockAuth();
        when(bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(user, 100L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            bookmarkService.removeBookmark(100L);
        });
    }

    @Test
    public void testIsBookmarked_Success() {
        mockAuth();
        Question question = Question.builder().id(100L).tenant(tenant1).deleted(false).build();
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(bookmarkRepository.existsByUserAndQuestion_IdAndDeletedFalse(user, 100L)).thenReturn(true);

        boolean bookmarked = bookmarkService.isBookmarked(100L);
        assertTrue(bookmarked);
    }
}
