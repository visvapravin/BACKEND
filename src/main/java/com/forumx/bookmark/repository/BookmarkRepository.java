package com.forumx.bookmark.repository;

import com.forumx.auth.entity.User;
import com.forumx.bookmark.entity.Bookmark;
import com.forumx.question.entity.Question;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // Find active bookmark by user and question
    Optional<Bookmark> findByUserAndQuestionAndDeletedFalse(User user, Question question);

    // Find bookmark including deleted to support restoration
    Optional<Bookmark> findByUserAndQuestion(User user, Question question);

    // Find active bookmark by user and question id (optimized for deletion/checking)
    Optional<Bookmark> findByUserAndQuestion_IdAndDeletedFalse(User user, Long questionId);

    // Exists check
    boolean existsByUserAndQuestion_IdAndDeletedFalse(User user, Long questionId);

    // Tenant-isolated lookups for a user's private bookmarks
    Page<Bookmark> findByTenant_IdAndUser_IdAndDeletedFalse(Long tenantId, Long userId, Pageable pageable);

    // General user list of active bookmarks
    Page<Bookmark> findByUserAndDeletedFalse(User user, Pageable pageable);
}
