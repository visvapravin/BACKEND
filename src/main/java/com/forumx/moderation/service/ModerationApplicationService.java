package com.forumx.moderation.service;

import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationHistoryResponse;
import com.forumx.moderation.dto.response.ModerationReportResponse;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModerationApplicationService {

    ModerationReportResponse createReport(CreateReportRequest request);

    ModerationReportResponse claimForReview(Long reportId);

    ModerationReportResponse applyDecision(Long reportId, ModerationDecisionRequest request);

    Page<ModerationReportResponse> getReports(
            ReportStatus status, ReportPriority priority, ReportReason reason, Pageable pageable);

    ModerationReportResponse getReportById(Long reportId);

    ModerationStatisticsResponse getStatistics();

    List<ModerationHistoryResponse> getHistory(Long reportId);
}
