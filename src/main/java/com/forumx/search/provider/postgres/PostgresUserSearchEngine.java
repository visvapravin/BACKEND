package com.forumx.search.provider.postgres;

import com.forumx.auth.entity.User;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import com.forumx.search.provider.UserSearchEngine;
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
public class PostgresUserSearchEngine implements UserSearchEngine {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Page<UserSearchResult> searchUsers(
            Long tenantId, String queryStr, UserSearchFilter filter, SearchSort sort, Pageable pageable, boolean includeEmail) {

        StringBuilder sql = new StringBuilder("SELECT DISTINCT u.* ");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(DISTINCT u.id) ");
        StringBuilder fromWhere = new StringBuilder("FROM users u ");

        if (filter != null && filter.role() != null) {
            fromWhere.append("JOIN user_roles ur ON u.id = ur.user_id JOIN roles r ON ur.role_id = r.id ");
        }

        fromWhere.append("WHERE u.deleted = false ");

        if (tenantId != null) {
            fromWhere.append("AND u.tenant_id = :tenantId ");
        }

        if (filter != null) {
            if (filter.active() != null) {
                fromWhere.append("AND u.enabled = :active ");
            }
            if (filter.role() != null) {
                fromWhere.append("AND r.role_name = :roleName ");
            }
        }

        boolean hasQuery = queryStr != null && !queryStr.isBlank();
        if (hasQuery) {
            fromWhere.append("AND (to_tsvector('english', coalesce(u.username,'')) @@ plainto_tsquery('english', :query) ")
                     .append("OR LOWER(u.username) LIKE LOWER(:likeQuery)) ");
        }

        sql.append(fromWhere);
        countSql.append(fromWhere);

        // Sorting
        SearchSort effectiveSort = sort != null ? sort : SearchSort.RELEVANCE;
        switch (effectiveSort) {
            case NEWEST -> sql.append("ORDER BY u.created_at DESC ");
            case OLDEST -> sql.append("ORDER BY u.created_at ASC ");
            case RELEVANCE -> {
                if (hasQuery) {
                    sql.append("ORDER BY ts_rank(to_tsvector('english', coalesce(u.username,'')), plainto_tsquery('english', :query)) DESC, u.created_at DESC ");
                } else {
                    sql.append("ORDER BY u.created_at DESC ");
                }
            }
            default -> sql.append("ORDER BY u.created_at DESC ");
        }

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), User.class);
        Query nativeCountQuery = entityManager.createNativeQuery(countSql.toString());

        if (tenantId != null) {
            nativeQuery.setParameter("tenantId", tenantId);
            nativeCountQuery.setParameter("tenantId", tenantId);
        }

        if (filter != null) {
            if (filter.active() != null) {
                nativeQuery.setParameter("active", filter.active());
                nativeCountQuery.setParameter("active", filter.active());
            }
            if (filter.role() != null) {
                nativeQuery.setParameter("roleName", filter.role().name());
                nativeCountQuery.setParameter("roleName", filter.role().name());
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

        List<User> users = nativeQuery.getResultList();
        long total = ((Number) nativeCountQuery.getSingleResult()).longValue();

        List<UserSearchResult> results = users.stream().map(u -> new UserSearchResult(
                u.getId(),
                u.getUsername(),
                includeEmail ? u.getEmail() : null,
                u.isEnabled(),
                u.getCreatedAt()
        )).toList();

        return new PageImpl<>(results, pageable, total);
    }
}
