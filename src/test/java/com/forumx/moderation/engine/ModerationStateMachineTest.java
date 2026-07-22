package com.forumx.moderation.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.exception.InvalidModerationStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModerationStateMachineTest {

    private ModerationStateMachine stateMachine;

    @BeforeEach
    public void setUp() {
        stateMachine = new ModerationStateMachine();
    }

    @Test
    public void testValidTransitions() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(ReportStatus.OPEN, ReportStatus.IN_REVIEW));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ReportStatus.IN_REVIEW, ReportStatus.RESOLVED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ReportStatus.IN_REVIEW, ReportStatus.REJECTED));
    }

    @Test
    public void testInvalidTransitions() {
        assertThrows(InvalidModerationStateException.class, () -> 
                stateMachine.validateTransition(ReportStatus.OPEN, ReportStatus.RESOLVED));

        assertThrows(InvalidModerationStateException.class, () -> 
                stateMachine.validateTransition(ReportStatus.OPEN, ReportStatus.REJECTED));

        assertThrows(InvalidModerationStateException.class, () -> 
                stateMachine.validateTransition(ReportStatus.RESOLVED, ReportStatus.OPEN));

        assertThrows(InvalidModerationStateException.class, () -> 
                stateMachine.validateTransition(ReportStatus.REJECTED, ReportStatus.IN_REVIEW));

        assertThrows(InvalidModerationStateException.class, () -> 
                stateMachine.validateTransition(ReportStatus.OPEN, ReportStatus.OPEN));
    }
}
