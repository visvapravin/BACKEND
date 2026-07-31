package com.forumx.moderation.engine.executor.impl;

import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.comment.entity.Comment;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.moderation.engine.executor.ModerationActionExecutor;
import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ModerationTarget;
import com.forumx.question.entity.Question;
import com.forumx.question.entity.QuestionStatus;
import com.forumx.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultModerationActionExecutor implements ModerationActionExecutor {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final com.forumx.presence.service.PresenceService presenceService;


    @Override
    public void execute(Long tenantId, ModerationAction action, ModerationTarget target, String notes) {
        log.info("Executing moderation action={} on targetType={} targetId={} in tenantId={}", 
                action, target.getTargetType(), target.getTargetId(), tenantId);

        switch (action) {
            case HIDE_CONTENT, DELETE_CONTENT -> handleHideOrDelete(target);
            case SUSPEND_USER, BAN_USER -> handleSuspendOrBan(target, tenantId);
            case WARN_USER -> log.warn("WARN_USER applied to owner of targetType={} targetId={}. Notes: {}", 
                    target.getTargetType(), target.getTargetId(), notes);
            case DISMISS -> log.info("DISMISS action applied; no content alteration required.");
        }
    }

    private void handleHideOrDelete(ModerationTarget target) {
        switch (target.getTargetType()) {
            case QUESTION -> {
                Question question = questionRepository.findById(target.getTargetId()).orElse(null);
                if (question != null) {
                    question.setDeleted(true);
                    question.setStatus(QuestionStatus.CLOSED);
                    questionRepository.save(question);
                    log.info("Soft deleted and closed Question id={}", target.getTargetId());
                }
            }
            case ANSWER -> {
                Answer answer = answerRepository.findById(target.getTargetId()).orElse(null);
                if (answer != null) {
                    answer.setDeleted(true);
                    answerRepository.save(answer);
                    log.info("Soft deleted Answer id={}", target.getTargetId());
                }
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(target.getTargetId()).orElse(null);
                if (comment != null) {
                    comment.setDeleted(true);
                    commentRepository.save(comment);
                    log.info("Soft deleted Comment id={}", target.getTargetId());
                }
            }
        }
    }

    private void handleSuspendOrBan(ModerationTarget target, Long tenantId) {

        Long userId = null;
        switch (target.getTargetType()) {
            case QUESTION -> {
                Question q = questionRepository.findById(target.getTargetId()).orElse(null);
                if (q != null && q.getAuthor() != null) {
                    userId = q.getAuthor().getId();
                }
            }
            case ANSWER -> {
                Answer a = answerRepository.findById(target.getTargetId()).orElse(null);
                if (a != null && a.getAuthor() != null) {
                    userId = a.getAuthor().getId();
                }
            }
            case COMMENT -> {
                Comment c = commentRepository.findById(target.getTargetId()).orElse(null);
                if (c != null && c.getAuthor() != null) {
                    userId = c.getAuthor().getId();
                }
            }
        }

        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setEnabled(false);
                userRepository.save(user);
                Long uTenantId = user.getTenant() != null ? user.getTenant().getId() : tenantId;
                if (presenceService != null) {
                    presenceService.evictPresence(user.getId(), uTenantId);
                }
                log.info("Suspended/Banned User id={} username={}", user.getId(), user.getUsername());
            }

        }
    }
}
