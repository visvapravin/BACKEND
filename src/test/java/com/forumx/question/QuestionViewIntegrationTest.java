package com.forumx.question;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.question.repository.QuestionViewRepository;
import com.forumx.question.service.QuestionService;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forumx.ForumXApplication;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
public class QuestionViewIntegrationTest {


    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionViewRepository questionViewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private User userA;
    private User userB;
    private User userTenant2;
    private Question question1;
    private Question question2;

    @BeforeEach
    void setUp() {
        tenant1 = tenantRepository.save(Tenant.builder()
                .name("Tenant 1 views " + System.currentTimeMillis())
                .slug("tenant-1-views-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenant2 = tenantRepository.save(Tenant.builder()
                .name("Tenant 2 views " + System.currentTimeMillis())
                .slug("tenant-2-views-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());



        userA = userRepository.save(User.builder()
                .tenant(tenant1)
                .username("usera_" + System.currentTimeMillis())
                .email("usera_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .enabled(true)
                .emailVerified(true)
                .build());

        userB = userRepository.save(User.builder()
                .tenant(tenant1)
                .username("userb_" + System.currentTimeMillis())
                .email("userb_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .enabled(true)
                .emailVerified(true)
                .build());

        userTenant2 = userRepository.save(User.builder()
                .tenant(tenant2)
                .username("usert2_" + System.currentTimeMillis())
                .email("usert2_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .enabled(true)
                .emailVerified(true)
                .build());

        authenticateAs(userA, tenant1);
        QuestionResponse q1Resp = questionService.createQuestion(new CreateQuestionRequest("Question 1", "Content 1"));
        QuestionResponse q2Resp = questionService.createQuestion(new CreateQuestionRequest("Question 2", "Content 2"));
        question1 = questionRepository.findById(q1Resp.getId()).orElseThrow();
        question2 = questionRepository.findById(q2Resp.getId()).orElseThrow();
    }

    private void authenticateAs(User user, Tenant tenant) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void userAFirstGet_IncrementsViewCountByOne() {
        authenticateAs(userA, tenant1);
        int initialCount = question1.getViewCount();

        QuestionResponse response = questionService.getQuestion(question1.getId());

        assertEquals(initialCount + 1, response.getViewCount());
        assertEquals(1, questionViewRepository.count());
    }

    @Test
    void userARepeatedGet_DoesNotIncrementViewCount() {
        authenticateAs(userA, tenant1);

        QuestionResponse r1 = questionService.getQuestion(question1.getId());
        QuestionResponse r2 = questionService.getQuestion(question1.getId());
        QuestionResponse r3 = questionService.getQuestion(question1.getId());

        assertEquals(1, r1.getViewCount());
        assertEquals(1, r2.getViewCount());
        assertEquals(1, r3.getViewCount());
    }

    @Test
    void userBGet_IncrementsViewCountToTwo() {
        authenticateAs(userA, tenant1);
        questionService.getQuestion(question1.getId());

        authenticateAs(userB, tenant1);
        QuestionResponse r2 = questionService.getQuestion(question1.getId());

        assertEquals(2, r2.getViewCount());
    }

    @Test
    void differentQuestionsTrackedIndependently() {
        authenticateAs(userA, tenant1);
        questionService.getQuestion(question1.getId());

        QuestionResponse q1Response = questionService.getQuestion(question1.getId());
        QuestionResponse q2Response = questionService.getQuestion(question2.getId());

        assertEquals(1, q1Response.getViewCount());
        assertEquals(1, q2Response.getViewCount());
    }

    @Test
    void tenantIsolation_UserFromOtherTenantCannotView() {
        authenticateAs(userTenant2, tenant2);

        assertThrows(AccessDeniedException.class, () -> questionService.getQuestion(question1.getId()));
    }

    @Test
    void deletedQuestion_Inaccessible() {
        authenticateAs(userA, tenant1);
        questionService.deleteQuestion(question1.getId());

        assertThrows(EntityNotFoundException.class, () -> questionService.getQuestion(question1.getId()));
    }
}
