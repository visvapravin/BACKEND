package com.forumx.moderation.service;

import com.forumx.auth.entity.User;
import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.entity.ModerationHistory;
import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModerationService {

    ModerationReport createReport(Long tenantId, User reporter, CreateReportRequest request);

    ModerationReport claimForReview(Long tenantId, Long reportId, User moderator);

    ModerationReport applyDecision(Long tenantId, Long reportId, User moderator, ModerationDecisionRequest request);

    Page<ModerationReport> getReports(
            Long tenantId, ReportStatus status, ReportPriority priority, ReportReason reason, Pageable pageable);

    ModerationReport getReportById(Long tenantId, Long reportId);

    ModerationStatisticsResponse getStatistics(Long tenantId);

    List<ModerationHistory> getHistory(Long reportId);
}
