package com.forumx.question.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.common.exception.InvalidSearchQueryException;
import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
public class SearchServiceImplTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private TenantResolver tenantResolver;

    @InjectMocks
    private SearchServiceImpl searchService;

    private Tenant tenant;
    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).name("Test Tenant").build();
        user = User.builder().id(10L).username("testuser").tenant(tenant).enabled(true).build();
        userDetails = new CustomUserDetails(user);
    }

    private void mockSecurityContext() {
        lenient().when(tenantResolver.resolveTenantId()).thenReturn(1L);
        lenient().when(authenticationFacade.getCurrentUserDetails()).thenReturn(userDetails);
        lenient().when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(user));
    }

    @Test
    public void testSearchQuestions_Success() {
        // Given
        mockSecurityContext();
        String keyword = "  spring  "; // has padding spaces
        Pageable pageable = PageRequest.of(0, 10);
        
        SearchResultResponse result = new SearchResultResponse(100L, "Spring Boot", "A spring framework question", "author", 0, 0, Instant.now());
        Page<SearchResultResponse> expectedPage = new PageImpl<>(List.of(result), pageable, 1);
        
        when(questionRepository.searchByKeyword(1L, "spring", pageable)).thenReturn(expectedPage);

        // When
        Page<SearchResultResponse> actualPage = searchService.searchQuestions(keyword, pageable);

        // Then
        assertNotNull(actualPage);
        assertEquals(1, actualPage.getTotalElements());
        assertEquals("Spring Boot", actualPage.getContent().getFirst().getTitle());
        verify(questionRepository, times(1)).searchByKeyword(1L, "spring", pageable);
    }

    @Test
    public void testSearchQuestions_NullKeywordThrowsException() {
        // Given
        mockSecurityContext();
        Pageable pageable = PageRequest.of(0, 10);

        // When & Then
        InvalidSearchQueryException exception = assertThrows(InvalidSearchQueryException.class, () -> {
            searchService.searchQuestions(null, pageable);
        });
        assertEquals("Search query must not be blank", exception.getMessage());
    }

    @Test
    public void testSearchQuestions_BlankKeywordThrowsException() {
        // Given
        mockSecurityContext();
        Pageable pageable = PageRequest.of(0, 10);

        // When & Then
        InvalidSearchQueryException exception = assertThrows(InvalidSearchQueryException.class, () -> {
            searchService.searchQuestions("   ", pageable);
        });
        assertEquals("Search query must not be blank", exception.getMessage());
    }

    @Test
    public void testSearchQuestions_InvalidSortPropertyThrowsException() {
        // Given
        mockSecurityContext();
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id")); // 'id' is not in allowed sort properties

        // When & Then
        InvalidSearchQueryException exception = assertThrows(InvalidSearchQueryException.class, () -> {
            searchService.searchQuestions("spring", pageable);
        });
        assertEquals("Sorting by property 'id' is not supported", exception.getMessage());
    }

    @Test
    public void testSearchQuestions_ValidSortPropertiesAccepted() {
        // Given
        mockSecurityContext();
        String[] allowedProps = {"createdAt", "voteScore", "answerCount", "title"};
        Pageable pageable;
        
        for (String prop : allowedProps) {
            pageable = PageRequest.of(0, 10, Sort.by(prop));
            when(questionRepository.searchByKeyword(eq(1L), eq("spring"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

            // When
            Page<SearchResultResponse> actual = searchService.searchQuestions("spring", pageable);

            // Then
            assertNotNull(actual);
        }
    }

    @Test
    public void testSearchQuestions_UnauthenticatedThrowsException() {
        // Given
        when(tenantResolver.resolveTenantId()).thenReturn(null);

        // When & Then
        assertThrows(AccessDeniedException.class, () -> {
            searchService.searchQuestions("spring", PageRequest.of(0, 10));
        });
    }

    @Test
    public void testSearchQuestions_DisabledUserThrowsException() {
        // Given
        user.setEnabled(false);
        mockSecurityContext();

        // When & Then
        assertThrows(AccessDeniedException.class, () -> {
            searchService.searchQuestions("spring", PageRequest.of(0, 10));
        });
    }
}
