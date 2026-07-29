package com.forumx.moderation.controller;

import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationHistoryResponse;
import com.forumx.moderation.dto.response.ModerationReportResponse;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.service.ModerationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/moderation")
@Tag(name = "Enterprise Moderation System", description = "Operations for content reporting, queue reviews, audit trails, and decisions")
public class ModerationController {

    private final ModerationApplicationService moderationApplicationService;

    @PostMapping("/reports")
    @Operation(summary = "Report Content", description = "Submit a moderation report for a question, answer, or comment")
    @ApiResponse(responseCode = "201", description = "Report submitted successfully")
    public ResponseEntity<ModerationReportResponse> createReport(
            @Valid @RequestBody CreateReportRequest request
    ) {
        ModerationReportResponse response = moderationApplicationService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('MODERATOR', 'TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get Moderation Queue", description = "Retrieve list of reports filtered by status, priority, and reason")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved moderation queue reports")
    public ResponseEntity<Page<ModerationReportResponse>> getReports(
            @Parameter(description = "Filter by report status") @RequestParam(value = "status", required = false) ReportStatus status,
            @Parameter(description = "Filter by report priority") @RequestParam(value = "priority", required = false) ReportPriority priority,
            @Parameter(description = "Filter by report reason") @RequestParam(value = "reason", required = false) ReportReason reason,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ModerationReportResponse> response = moderationApplicationService.getReports(status, priority, reason, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "Get Report Details", description = "Retrieve details of a report by its ID")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved report details")
    public ResponseEntity<ModerationReportResponse> getReport(
            @PathVariable("id") Long reportId
    ) {
        ModerationReportResponse response = moderationApplicationService.getReportById(reportId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/reports/{id}/review")
    @PreAuthorize("hasAnyRole('MODERATOR', 'TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Claim Report for Review", description = "Mark the report as IN_REVIEW and assign it to the current moderator")
    @ApiResponse(responseCode = "200", description = "Successfully claimed report for review")
    public ResponseEntity<ModerationReportResponse> claimForReview(
            @PathVariable("id") Long reportId
    ) {
        ModerationReportResponse response = moderationApplicationService.claimForReview(reportId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/reports/{id}/decision")
    @PreAuthorize("hasAnyRole('MODERATOR', 'TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Apply Moderation Decision", description = "Commit a resolution action (warn, hide, delete, suspend, ban, reject) and record to audit logs")
    @ApiResponse(responseCode = "200", description = "Successfully applied moderation decision")
    public ResponseEntity<ModerationReportResponse> applyDecision(
            @PathVariable("id") Long reportId,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        ModerationReportResponse response = moderationApplicationService.applyDecision(reportId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('MODERATOR', 'TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get Moderation Statistics", description = "Retrieve open/closed counts, SLA resolution times, and categories")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved moderation statistics")
    public ResponseEntity<ModerationStatisticsResponse> getStatistics() {
        ModerationStatisticsResponse response = moderationApplicationService.getStatistics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/{id}/history")
    @PreAuthorize("hasAnyRole('MODERATOR', 'TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get Report Audit Trail", description = "Retrieve immutable history log of all status transitions and actions for a report")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved report history")
    public ResponseEntity<List<ModerationHistoryResponse>> getHistory(
            @PathVariable("id") Long reportId
    ) {
        List<ModerationHistoryResponse> response = moderationApplicationService.getHistory(reportId);
        return ResponseEntity.ok(response);
    }
}
