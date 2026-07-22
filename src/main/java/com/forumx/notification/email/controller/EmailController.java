package com.forumx.notification.email.controller;

import com.forumx.notification.email.dto.EmailNotificationCommand;
import com.forumx.notification.email.dto.request.TestEmailRequest;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.tenant.resolver.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/email")
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class EmailController {

    private final NotificationApplicationService notificationApplicationService;
    private final TenantResolver tenantResolver;

    @PostMapping("/test")
    public ResponseEntity<Void> testEmail(@RequestBody TestEmailRequest request) {
        Long tenantId = tenantResolver.resolveTenantId();
        log.info("Test endpoint triggered for email send. recipient={}, tenantId={}", request.getRecipientEmail(), tenantId);

        EmailNotificationCommand command = new EmailNotificationCommand(
                tenantId,
                request.getRecipientEmail(),
                request.getRecipientUsername(),
                request.getTemplateType(),
                request.getTemplateModel()
        );

        notificationApplicationService.queueEmail(command);
        return ResponseEntity.ok().build();
    }
}
