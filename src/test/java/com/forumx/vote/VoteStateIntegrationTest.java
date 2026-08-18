package com.forumx.vote;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.service.AnswerService;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.service.CommentService;
import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;
import com.forumx.question.service.QuestionService;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.vote.dto.request.CreateVoteRequest;
import com.forumx.vote.dto.response.ScoreResponse;
import com.forumx.vote.entity.VoteType;
import com.forumx.vote.service.VoteService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forumx.ForumXApplication;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
public class VoteStateIntegrationTest {


    @Autowired
    private VoteService voteService;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private AnswerService answerService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private User author;
    private User userA;
    private User userB;
    private User userTenant2;
    private QuestionResponse question;
    private AnswerResponse answer;
    private CommentResponse comment;

    @BeforeEach
    void setUp() {
        tenant1 = tenantRepository.save(Tenant.builder()
                .name("Tenant 1 votes " + System.currentTimeMillis())
                .slug("tenant-1-votes-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenant2 = tenantRepository.save(Tenant.builder()
                .name("Tenant 2 votes " + System.currentTimeMillis())
                .slug("tenant-2-votes-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());



        author = userRepository.save(User.builder()
                .tenant(tenant1)
                .username("author_" + System.currentTimeMillis())
                .email("author_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .enabled(true)
                .emailVerified(true)
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

        authenticateAs(author, tenant1);
        question = questionService.createQuestion(new CreateQuestionRequest("Target Question", "Question Content"));
        answer = answerService.createAnswer(question.getId(), new CreateAnswerRequest("Answer Content"));
        comment = commentService.createForQuestion(question.getId(), new CreateCommentRequest("Comment Content"));
    }

    private void authenticateAs(User user, Tenant tenant) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void initialScore_ReturnsZeroScoreAndNullUserVote() {
        authenticateAs(userA, tenant1);

        ScoreResponse qScore = voteService.getQuestionScore(question.getId());
        ScoreResponse aScore = voteService.getAnswerScore(answer.getId());
        ScoreResponse cScore = voteService.getCommentScore(comment.getId());

        assertEquals(0L, qScore.score());
        assertNull(qScore.userVote());
        assertEquals("QUESTION", qScore.targetType());

        assertEquals(0L, aScore.score());
        assertNull(aScore.userVote());
        assertEquals("ANSWER", aScore.targetType());

        assertEquals(0L, cScore.score());
        assertNull(cScore.userVote());
        assertEquals("COMMENT", cScore.targetType());
    }

    @Test
    void userAUpvotes_UserAReadsUpvote_UserBReadsNullUserVote() {
        authenticateAs(userA, tenant1);
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.UPVOTE));

        ScoreResponse qScoreA = voteService.getQuestionScore(question.getId());
        assertEquals(1L, qScoreA.score());
        assertEquals(VoteType.UPVOTE, qScoreA.userVote());

        authenticateAs(userB, tenant1);
        ScoreResponse qScoreB = voteService.getQuestionScore(question.getId());
        assertEquals(1L, qScoreB.score());
        assertNull(qScoreB.userVote());
    }

    @Test
    void userBDownvotes_ScoreDecrements_UserBUserVoteIsDownvote() {
        authenticateAs(userA, tenant1);
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.UPVOTE));

        authenticateAs(userB, tenant1);
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.DOWNVOTE));

        ScoreResponse qScoreB = voteService.getQuestionScore(question.getId());
        assertEquals(0L, qScoreB.score());
        assertEquals(VoteType.DOWNVOTE, qScoreB.userVote());

        authenticateAs(userA, tenant1);
        ScoreResponse qScoreA = voteService.getQuestionScore(question.getId());
        assertEquals(0L, qScoreA.score());
        assertEquals(VoteType.UPVOTE, qScoreA.userVote());
    }

    @Test
    void voteSwitchingAndToggling_PreservesCorrectState() {
        authenticateAs(userA, tenant1);

        // 1. First Upvote -> score 1, UPVOTE
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.UPVOTE));
        ScoreResponse s1 = voteService.getQuestionScore(question.getId());
        assertEquals(1L, s1.score());
        assertEquals(VoteType.UPVOTE, s1.userVote());

        // 2. Switch to Downvote -> score -1, DOWNVOTE
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.DOWNVOTE));
        ScoreResponse s2 = voteService.getQuestionScore(question.getId());
        assertEquals(-1L, s2.score());
        assertEquals(VoteType.DOWNVOTE, s2.userVote());

        // 3. Repeat Downvote (Toggle off) -> score 0, null
        voteService.voteQuestion(question.getId(), new CreateVoteRequest(VoteType.DOWNVOTE));
        ScoreResponse s3 = voteService.getQuestionScore(question.getId());
        assertEquals(0L, s3.score());
        assertNull(s3.userVote());
    }

    @Test
    void tenantIsolation_CrossTenantScoreReadDenied() {
        authenticateAs(userTenant2, tenant2);

        assertThrows(AccessDeniedException.class, () -> voteService.getQuestionScore(question.getId()));
        assertThrows(AccessDeniedException.class, () -> voteService.getAnswerScore(answer.getId()));
        assertThrows(AccessDeniedException.class, () -> voteService.getCommentScore(comment.getId()));
    }
}
