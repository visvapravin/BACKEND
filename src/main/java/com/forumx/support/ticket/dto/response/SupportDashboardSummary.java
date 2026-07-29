package com.forumx.support.ticket.dto.response;

public record SupportDashboardSummary(
    long waiting,
    long open,
    long closed,
    long resolvedToday,
    long onlineModerators
) {}


