package com.forumx.moderation.repository;

import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ModerationTargetType;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationReportRepository extends JpaRepository<ModerationReport, Long>, JpaSpecificationExecutor<ModerationReport> {

    Optional<ModerationReport> findByTenant_IdAndTarget_TargetTypeAndTarget_TargetIdAndReasonAndStatusIn(
            Long tenantId,
            ModerationTargetType targetType,
            Long targetId,
            ReportReason reason,
            Collection<ReportStatus> statuses
    );

    Page<ModerationReport> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    long countByTenant_IdAndStatus(Long tenantId, ReportStatus status);

    long countByTenant_IdAndStatusIn(Long tenantId, Collection<ReportStatus> statuses);
}
