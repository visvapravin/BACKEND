package com.forumx.bookmark.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.bookmark.entity.Bookmark;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BookmarkRepositoryTest {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private QuestionRepository questionRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private User user1;
    private Question question1;

    @BeforeEach
    public void setUp() {
        bookmarkRepository.deleteAllInBatch();

        tenant1 = tenantRepository.save(Tenant.builder()
                .name("Bookmark Repo Tenant 1")
                .slug("bookmark-t1-repo-test")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenant2 = tenantRepository.save(Tenant.builder()
                .name("Bookmark Repo Tenant 2")
                .slug("bookmark-t2-repo-test")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        user1 = userRepository.save(User.builder()
                .username("buser1")
                .email("buser1@test.com")
                .tenant(tenant1)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("buser2")
                .email("buser2@test.com")
                .tenant(tenant2)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        question1 = questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(user1)
                .title("Q1 Title")
                .content("Q1 Content")
                .build());
    }

    @Test
    public void testSaveAndFindQueries() {
        Bookmark bookmark = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();
        bookmark = bookmarkRepository.save(bookmark);

        Optional<Bookmark> active = bookmarkRepository.findByUserAndQuestionAndDeletedFalse(user1, question1);
        assertTrue(active.isPresent());
        assertEquals(bookmark.getId(), active.get().getId());

        Optional<Bookmark> byIdAndActive = bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(user1, question1.getId());
        assertTrue(byIdAndActive.isPresent());
        assertEquals(bookmark.getId(), byIdAndActive.get().getId());

        assertTrue(bookmarkRepository.existsByUserAndQuestion_IdAndDeletedFalse(user1, question1.getId()));
    }

    @Test
    public void testSoftDeleteAndRestorationBehavior() {
        Bookmark bookmark = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();
        bookmark = bookmarkRepository.save(bookmark);

        // Soft delete
        bookmark.remove();
        bookmarkRepository.save(bookmark);

        assertFalse(bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(user1, question1.getId()).isPresent());
        assertFalse(bookmarkRepository.findByUserAndQuestionAndDeletedFalse(user1, question1).isPresent());

        // Find including deleted
        Optional<Bookmark> all = bookmarkRepository.findByUserAndQuestion(user1, question1);
        assertTrue(all.isPresent());
        assertTrue(all.get().isDeleted());

        // Restore
        all.get().restore();
        bookmarkRepository.save(all.get());

        assertTrue(bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(user1, question1.getId()).isPresent());
    }

    @Test
    public void testTenantIsolationLookups() {
        bookmarkRepository.save(Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build());

        Page<Bookmark> t1Bookmarks = bookmarkRepository.findByTenant_IdAndUser_IdAndDeletedFalse(
                tenant1.getId(), user1.getId(), PageRequest.of(0, 10));
        assertEquals(1, t1Bookmarks.getTotalElements());

        Page<Bookmark> t2Bookmarks = bookmarkRepository.findByTenant_IdAndUser_IdAndDeletedFalse(
                tenant2.getId(), user1.getId(), PageRequest.of(0, 10));
        assertEquals(0, t2Bookmarks.getTotalElements());
    }

    @Test
    public void testPartialUniqueIndex() {
        Bookmark bookmark1 = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();
        bookmarkRepository.saveAndFlush(bookmark1);

        // Second active bookmark should fail
        Bookmark bookmark2 = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            bookmarkRepository.saveAndFlush(bookmark2);
        });
    }

    @Test
    public void testBookmarkAgainAfterDeleted() {
        Bookmark bookmark1 = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();
        bookmark1 = bookmarkRepository.saveAndFlush(bookmark1);

        // Soft delete first
        bookmark1.remove();
        bookmarkRepository.saveAndFlush(bookmark1);

        // Creating another active one now should work
        Bookmark bookmark2 = Bookmark.builder()
                .tenant(tenant1)
                .user(user1)
                .question(question1)
                .build();

        assertDoesNotThrow(() -> {
            bookmarkRepository.saveAndFlush(bookmark2);
        });
    }
}
