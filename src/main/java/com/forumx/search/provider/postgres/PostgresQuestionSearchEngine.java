package com.forumx.search.provider.postgres;

import com.forumx.question.entity.Question;
import com.forumx.question.entity.QuestionStatus;
import com.forumx.search.config.SearchProperties;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.provider.QuestionSearchEngine;
import com.forumx.search.service.SearchSnippetService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresQuestionSearchEngine implements QuestionSearchEngine {

    @PersistenceContext
    private final EntityManager entityManager;

    private final SearchSnippetService snippetService;
    private final SearchProperties searchProperties;

    @Override
    @SuppressWarnings("unchecked")
    public Page<QuestionSearchResult> searchQuestions(
            Long tenantId, String queryStr, QuestionSearchFilter filter, SearchSort sort, Pageable pageable) {

        StringBuilder sql = new StringBuilder("SELECT q.* ");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(q.id) ");
        StringBuilder fromWhere = new StringBuilder("FROM questions q JOIN users u ON q.author_id = u.id WHERE q.deleted = false ");

        if (tenantId != null) {
            fromWhere.append("AND q.tenant_id = :tenantId ");
        }

        if (filter != null) {
            if (filter.author() != null && !filter.author().isBlank()) {
                fromWhere.append("AND (LOWER(u.username) LIKE LOWER(:author) OR u.id = :authorId) ");
            }
            if (filter.solved() != null) {
                if (filter.solved()) {
                    fromWhere.append("AND q.status = 'SOLVED' ");
                } else {
                    fromWhere.append("AND q.status != 'SOLVED' ");
                }
            }
            if (filter.createdAfter() != null) {
                fromWhere.append("AND q.created_at >= :createdAfter ");
            }
            if (filter.createdBefore() != null) {
                fromWhere.append("AND q.created_before <= :createdBefore ");
            }
        }

        boolean hasQuery = queryStr != null && !queryStr.isBlank();
        if (hasQuery) {
            fromWhere.append("AND (to_tsvector('english', coalesce(q.title,'') || ' ' || coalesce(q.content,'')) @@ plainto_tsquery('english', :query) ")
                     .append("OR LOWER(q.title) LIKE LOWER(:likeQuery) OR LOWER(q.content) LIKE LOWER(:likeQuery)) ");
        }

        sql.append(fromWhere);
        countSql.append(fromWhere);

        // Sorting
        SearchSort effectiveSort = sort != null ? sort : SearchSort.RELEVANCE;
        switch (effectiveSort) {
            case NEWEST -> sql.append("ORDER BY q.created_at DESC ");
            case OLDEST -> sql.append("ORDER BY q.created_at ASC ");
            case MOST_ANSWERED -> sql.append("ORDER BY q.answers_count DESC, q.created_at DESC ");
            case MOST_VIEWED -> sql.append("ORDER BY q.view_count DESC, q.created_at DESC ");
            case MOST_UPVOTED -> sql.append("ORDER BY q.vote_score DESC, q.created_at DESC ");
            case RELEVANCE -> {
                if (hasQuery) {
                    sql.append("ORDER BY ts_rank(to_tsvector('english', coalesce(q.title,'') || ' ' || coalesce(q.content,'')), plainto_tsquery('english', :query)) DESC, q.created_at DESC ");
                } else {
                    sql.append("ORDER BY q.created_at DESC ");
                }
            }
        }

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Question.class);
        Query nativeCountQuery = entityManager.createNativeQuery(countSql.toString());

        // Bind parameters
        if (tenantId != null) {
            nativeQuery.setParameter("tenantId", tenantId);
            nativeCountQuery.setParameter("tenantId", tenantId);
        }

        if (filter != null) {
            if (filter.author() != null && !filter.author().isBlank()) {
                nativeQuery.setParameter("author", "%" + filter.author() + "%");
                nativeCountQuery.setParameter("author", "%" + filter.author() + "%");
                Long authorId = -1L;
                try { authorId = Long.parseLong(filter.author()); } catch (NumberFormatException ignored) {}
                nativeQuery.setParameter("authorId", authorId);
                nativeCountQuery.setParameter("authorId", authorId);
            }
            if (filter.createdAfter() != null) {
                nativeQuery.setParameter("createdAfter", filter.createdAfter());
                nativeCountQuery.setParameter("createdAfter", filter.createdAfter());
            }
            if (filter.createdBefore() != null) {
                nativeQuery.setParameter("createdBefore", filter.createdBefore());
                nativeCountQuery.setParameter("createdBefore", filter.createdBefore());
            }
        }

        if (hasQuery) {
            nativeQuery.setParameter("query", queryStr);
            nativeCountQuery.setParameter("query", queryStr);
            nativeQuery.setParameter("likeQuery", "%" + queryStr + "%");
            nativeCountQuery.setParameter("likeQuery", "%" + queryStr + "%");
        }

        nativeQuery.setFirstResult((int) pageable.getOffset());
        nativeQuery.setMaxResults(pageable.getPageSize());

        List<Question> questions = nativeQuery.getResultList();
        long total = ((Number) nativeCountQuery.getSingleResult()).longValue();

        List<QuestionSearchResult> results = questions.stream().map(q -> new QuestionSearchResult(
                q.getId(),
                q.getTitle(),
                snippetService.generateSnippet(q.getContent(), queryStr),
                q.getAuthor() != null ? q.getAuthor().getUsername() : null,
                QuestionStatus.ANSWERED.equals(q.getStatus()),
                q.getViewCount() != null ? q.getViewCount() : 0,
                q.getAnswerCount() != null ? q.getAnswerCount() : 0,
                q.getCreatedAt()
        )).toList();

        return new PageImpl<>(results, pageable, total);
    }
}
