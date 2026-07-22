package com.forumx.support.chat.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.MessageDeliveryStatus;
import com.forumx.support.chat.entity.MessageType;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
public class ChatRepositoryTest {

    @Autowired private ChatSessionRepository chatSessionRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;
    @MockBean private com.forumx.redis.gateway.RedisGateway redisGateway;
    @MockBean private com.forumx.security.facade.AuthenticationFacade authenticationFacade;
    @MockBean private com.forumx.tenant.resolver.TenantResolver tenantResolver;

    @Autowired private com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    @Autowired private com.forumx.auth.repository.UserProfileRepository userProfileRepository;
    @Autowired private com.forumx.auth.verification.repository.VerificationTokenRepository verificationTokenRepository;
    @Autowired private com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private com.forumx.auth.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    private com.forumx.question.repository.QuestionRepository questionRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.comment.repository.CommentRepository commentRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private Ticket ticket;
    private ChatSession session;

    @BeforeEach
    public void setUp() {
        transactionTemplate.execute(status -> {
            ticketMessageRepository.deleteAllInBatch();
            reportRepository.deleteAllInBatch();
            commentRepository.deleteAllInBatch();
            notificationRepository.deleteAllInBatch();
            chatMessageRepository.deleteAllInBatch();
            chatSessionRepository.deleteAllInBatch();
            ticketRepository.deleteAllInBatch();
            answerRepository.deleteAllInBatch();
            bookmarkRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
            refreshTokenRepository.deleteAllInBatch();
            passwordResetTokenRepository.deleteAllInBatch();
            verificationTokenRepository.deleteAllInBatch();
            userProfileRepository.deleteAllInBatch();
            userRoleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            tenant = tenantRepository.findAll().stream()
                    .filter(t -> t.getSlug().equals("default"))
                    .findFirst()
                    .orElseGet(() -> tenantRepository.save(Tenant.builder()
                            .name("Default")
                            .slug("default")
                            .build()));

            customer = userRepository.save(User.builder()
                    .username("chat_cust")
                    .email("cust@chat.com")
                    .tenant(tenant)
                    .enabled(true)
                    .build());

            moderator = userRepository.save(User.builder()
                    .username("chat_mod")
                    .email("mod@chat.com")
                    .tenant(tenant)
                    .enabled(true)
                    .build());

            ticket = ticketRepository.save(Ticket.builder()
                    .tenant(tenant)
                    .creator(customer)
                    .assignedTo(moderator)
                    .subject("Chat test")
                    .description("Test ticket description")
                    .status(TicketStatus.OPEN)
                    .priority(TicketPriority.MEDIUM)
                    .version(0L)
                    .build());

            session = chatSessionRepository.save(ChatSession.builder()
                    .ticket(ticket)
                    .tenant(tenant)
                    .customer(customer)
                    .moderator(moderator)
                    .status(ChatSessionStatus.ACTIVE)
                    .version(0L)
                    .build());

            return null;
        });
    }

    @Test
    public void testFindByTicketAndTenant() {
        ChatSession found = chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticket.getId(), tenant.getId())
                .orElse(null);
        assertNotNull(found);
        assertEquals(session.getId(), found.getId());
    }

    @Test
    public void testCursorPaginationAndDeterministicOrdering() {
        transactionTemplate.execute(status -> {
            // Save 3 messages with set created time to ensure ordering
            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .sender(customer)
                    .messageType(MessageType.TEXT)
                    .content("First")
                    .deliveryStatus(MessageDeliveryStatus.SENT)
                    .build());

            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .sender(moderator)
                    .messageType(MessageType.TEXT)
                    .content("Second")
                    .deliveryStatus(MessageDeliveryStatus.SENT)
                    .build());

            ChatMessage m3 = chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .sender(customer)
                    .messageType(MessageType.TEXT)
                    .content("Third")
                    .deliveryStatus(MessageDeliveryStatus.SENT)
                    .build());

            // First page (size 2)
            Page<ChatMessage> firstPage = chatMessageRepository.findMessagesFirstPage(session.getId(), PageRequest.of(0, 2));
            assertEquals(2, firstPage.getContent().size());
            assertEquals("First", firstPage.getContent().get(0).getContent());
            assertEquals("Second", firstPage.getContent().get(1).getContent());

            // Next page using cursor (beforeMessageId = m3.getId())
            Page<ChatMessage> cursorPage = chatMessageRepository.findMessagesBefore(session.getId(), m3.getId(), PageRequest.of(0, 2));
            assertEquals(2, cursorPage.getContent().size());
            assertEquals("First", cursorPage.getContent().get(0).getContent());
            assertEquals("Second", cursorPage.getContent().get(1).getContent());

            return null;
        });
    }

    @Test
    public void testFindUnreadMessagesForMarkRead() {
        transactionTemplate.execute(status -> {
            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .sender(customer) // sent by customer
                    .messageType(MessageType.TEXT)
                    .content("From customer")
                    .deliveryStatus(MessageDeliveryStatus.SENT)
                    .build());

            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .sender(moderator) // sent by moderator
                    .messageType(MessageType.TEXT)
                    .content("From moderator")
                    .deliveryStatus(MessageDeliveryStatus.SENT)
                    .build());

            // Moderator wants to find unread messages from customer (senderId != moderatorId)
            List<ChatMessage> unreadForMod = chatMessageRepository.findBySession_IdAndSender_IdNotAndDeliveryStatusNotAndDeletedFalse(
                    session.getId(), moderator.getId(), MessageDeliveryStatus.READ
            );

            assertEquals(1, unreadForMod.size());
            assertEquals("From customer", unreadForMod.get(0).getContent());

            return null;
        });
    }
}
