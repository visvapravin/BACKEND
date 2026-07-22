package com.forumx.search.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.forumx.search.config.SearchProperties;
import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.response.GlobalSearchResponse;
import com.forumx.search.provider.AnswerSearchEngine;
import com.forumx.search.provider.QuestionSearchEngine;
import com.forumx.search.provider.UserSearchEngine;
import com.forumx.search.service.impl.SearchServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class SearchServiceImplTest {

    @Mock private QuestionSearchEngine questionSearchEngine;
    @Mock private AnswerSearchEngine answerSearchEngine;
    @Mock private UserSearchEngine userSearchEngine;

    private SearchService searchService;
    private SearchProperties searchProperties;

    @BeforeEach
    public void setUp() {
        searchProperties = new SearchProperties();
        searchService = new SearchServiceImpl(
                questionSearchEngine,
                answerSearchEngine,
                userSearchEngine,
                searchProperties
        );
    }

    @Test
    public void testGlobalSearchAggregatesAllResults() {
        QuestionSearchResult qResult = new QuestionSearchResult(
                1L, "RabbitMQ Question", "Snippet...", "alice", false, 10, 2, Instant.now());
        AnswerSearchResult aResult = new AnswerSearchResult(
                10L, 1L, "RabbitMQ Answer Snippet...", "bob", Instant.now());
        UserSearchResult uResult = new UserSearchResult(
                5L, "alice", "alice@example.com", true, Instant.now());

        when(questionSearchEngine.searchQuestions(eq(1L), eq("rabbitmq"), any(), eq(SearchSort.RELEVANCE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(qResult)));
        when(answerSearchEngine.searchAnswers(eq(1L), eq("rabbitmq"), any(), eq(SearchSort.RELEVANCE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aResult)));
        when(userSearchEngine.searchUsers(eq(1L), eq("rabbitmq"), any(), eq(SearchSort.RELEVANCE), any(Pageable.class), eq(true)))
                .thenReturn(new PageImpl<>(List.of(uResult)));

        GlobalSearchResponse response = searchService.globalSearch(1L, "rabbitmq", 5, true);

        assertNotNull(response);
        assertEquals(1, response.questions().size());
        assertEquals("RabbitMQ Question", response.questions().get(0).title());
        assertEquals(1, response.answers().size());
        assertEquals(1, response.users().size());
        assertEquals("POSTGRES", response.summary().provider());
        assertTrue(response.summary().executionTimeMs() >= 0);
    }
}
