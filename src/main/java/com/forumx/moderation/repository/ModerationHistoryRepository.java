package com.forumx.moderation.repository;

import com.forumx.moderation.entity.ModerationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationHistoryRepository extends JpaRepository<ModerationHistory, Long> {
    List<ModerationHistory> findByReport_IdOrderByCreatedAtDesc(Long reportId);
}
