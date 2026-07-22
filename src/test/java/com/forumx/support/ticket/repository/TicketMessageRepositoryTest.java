package com.forumx.support.ticket.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketMessage;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class TicketMessageRepositoryTest {

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    public void testSaveAndFindByTicketAndDeletedFalse() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Support Tenant")
                .slug("support-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        User customer = userRepository.save(User.builder()
                .username("customer_repo")
                .email("customer_repo@test.com")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .tenant(tenant)
                .creator(customer)
                .subject("Repository Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .build());

        TicketMessage msg1 = ticketMessageRepository.save(TicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .message("Hello support!")
                .messageUuid(UUID.randomUUID())
                .internalNote(false)
                .build());

        ticketMessageRepository.save(TicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .message("Any updates?")
                .messageUuid(UUID.randomUUID())
                .internalNote(false)
                .build());

        Page<TicketMessage> page = ticketMessageRepository.findByTicketAndDeletedFalse(
                ticket,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
        );

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(m -> m.getMessage().equals("Hello support!")));
        assertTrue(page.getContent().stream().anyMatch(m -> m.getMessage().equals("Any updates?")));

        // Verify soft delete
        msg1.setDeleted(true);
        msg1.setDeletedAt(java.time.Instant.now());
        ticketMessageRepository.save(msg1);

        Page<TicketMessage> pageAfterDelete = ticketMessageRepository.findByTicketAndDeletedFalse(
                ticket,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
        );
        assertEquals(1, pageAfterDelete.getTotalElements());
        assertEquals("Any updates?", pageAfterDelete.getContent().get(0).getMessage());
    }

    @Test
    public void testFindByIdAndDeletedFalse() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Support Tenant 2")
                .slug("support-tenant-2")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        User customer = userRepository.save(User.builder()
                .username("customer_repo_2")
                .email("customer_repo_2@test.com")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .tenant(tenant)
                .creator(customer)
                .subject("Repository Test Ticket 2")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .build());

        TicketMessage msg = ticketMessageRepository.save(TicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .message("Single message test")
                .messageUuid(UUID.randomUUID())
                .internalNote(false)
                .build());

        assertTrue(ticketMessageRepository.findByIdAndDeletedFalse(msg.getId()).isPresent());
        assertTrue(ticketMessageRepository.existsByIdAndDeletedFalse(msg.getId()));

        msg.setDeleted(true);
        ticketMessageRepository.save(msg);

        assertFalse(ticketMessageRepository.findByIdAndDeletedFalse(msg.getId()).isPresent());
        assertFalse(ticketMessageRepository.existsByIdAndDeletedFalse(msg.getId()));
    }
}
