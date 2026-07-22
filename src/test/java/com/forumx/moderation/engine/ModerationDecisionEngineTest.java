package com.forumx.moderation.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModerationDecisionEngineTest {

    private ModerationDecisionEngine decisionEngine;

    @BeforeEach
    public void setUp() {
        decisionEngine = new ModerationDecisionEngine();
    }

    @Test
    public void testPriorityAutoAssignment() {
        assertEquals(ReportPriority.LOW, decisionEngine.evaluatePriority(ReportReason.SPAM));
        assertEquals(ReportPriority.LOW, decisionEngine.evaluatePriority(ReportReason.DUPLICATE));
        
        assertEquals(ReportPriority.MEDIUM, decisionEngine.evaluatePriority(ReportReason.MISINFORMATION));
        assertEquals(ReportPriority.MEDIUM, decisionEngine.evaluatePriority(ReportReason.OTHER));

        assertEquals(ReportPriority.HIGH, decisionEngine.evaluatePriority(ReportReason.ABUSE));
        assertEquals(ReportPriority.HIGH, decisionEngine.evaluatePriority(ReportReason.COPYRIGHT));

        assertEquals(ReportPriority.CRITICAL, decisionEngine.evaluatePriority(ReportReason.HARASSMENT));
        assertEquals(ReportPriority.CRITICAL, decisionEngine.evaluatePriority(ReportReason.HATE_SPEECH));
    }

    @Test
    public void testActionAllowedMatrix() {
        // Disallowed actions
        assertFalse(decisionEngine.isActionAllowed(ReportReason.DUPLICATE, ModerationAction.DELETE_CONTENT));
        assertFalse(decisionEngine.isActionAllowed(ReportReason.SPAM, ModerationAction.BAN_USER));

        // Allowed actions
        assertTrue(decisionEngine.isActionAllowed(ReportReason.SPAM, ModerationAction.DISMISS));
        assertTrue(decisionEngine.isActionAllowed(ReportReason.HARASSMENT, ModerationAction.BAN_USER));
        assertTrue(decisionEngine.isActionAllowed(ReportReason.HATE_SPEECH, ModerationAction.HIDE_CONTENT));
    }
}
