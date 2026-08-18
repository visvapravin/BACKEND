package com.forumx.search.provider.postgres;

import com.forumx.answer.entity.Answer;
import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.provider.AnswerSearchEngine;
import com.forumx.search.service.SearchSnippetService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresAnswerSearchEngine implements AnswerSearchEngine {

    @PersistenceContext
    private final EntityManager entityManager;

    private final SearchSnippetService snippetService;

    @Override
    @SuppressWarnings("unchecked")
    public Page<AnswerSearchResult> searchAnswers(
            Long tenantId, String queryStr, AnswerSearchFilter filter, SearchSort sort, Pageable pageable) {

        StringBuilder sql = new StringBuilder("SELECT a.* ");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(a.id) ");
        StringBuilder fromWhere = new StringBuilder("FROM answers a JOIN users u ON a.author_id = u.id WHERE a.deleted = false ");

        if (tenantId != null) {
            fromWhere.append("AND a.tenant_id = :tenantId ");
        }

        if (filter != null) {
            if (filter.author() != null && !filter.author().isBlank()) {
                fromWhere.append("AND (LOWER(u.username) LIKE LOWER(:author) OR u.id = :authorId) ");
            }
            if (filter.createdAfter() != null) {
                fromWhere.append("AND a.created_at >= :createdAfter ");
            }
            if (filter.createdBefore() != null) {
                fromWhere.append("AND a.created_at <= :createdBefore ");
            }
        }

        boolean hasQuery = queryStr != null && !queryStr.isBlank();
        if (hasQuery) {
            fromWhere.append("AND (to_tsvector('english', coalesce(a.content,'')) @@ plainto_tsquery('english', :query) ")
                     .append("OR LOWER(a.content) LIKE LOWER(:likeQuery)) ");
        }

        sql.append(fromWhere);
        countSql.append(fromWhere);

        // Sorting
        SearchSort effectiveSort = sort != null ? sort : SearchSort.RELEVANCE;
        switch (effectiveSort) {
            case NEWEST -> sql.append("ORDER BY a.created_at DESC ");
            case OLDEST -> sql.append("ORDER BY a.created_at ASC ");
            case RELEVANCE -> {
                if (hasQuery) {
                    sql.append("ORDER BY ts_rank(to_tsvector('english', coalesce(a.content,'')), plainto_tsquery('english', :query)) DESC, a.created_at DESC ");
                } else {
                    sql.append("ORDER BY a.created_at DESC ");
                }
            }
            default -> sql.append("ORDER BY a.created_at DESC ");
        }

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Answer.class);
        Query nativeCountQuery = entityManager.createNativeQuery(countSql.toString());

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

        List<Answer> answers = nativeQuery.getResultList();
        long total = ((Number) nativeCountQuery.getSingleResult()).longValue();

        List<AnswerSearchResult> results = answers.stream().map(a -> new AnswerSearchResult(
                a.getId(),
                a.getQuestion() != null ? a.getQuestion().getId() : null,
                snippetService.generateSnippet(a.getContent(), queryStr),
                a.getAuthor() != null ? a.getAuthor().getUsername() : null,
                a.getCreatedAt()
        )).toList();

        return new PageImpl<>(results, pageable, total);
    }
}
