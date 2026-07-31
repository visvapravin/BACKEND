package com.forumx.support.chat.dto.response;

import java.util.List;

/** Chronological chat slice with an opaque-to-the-client message cursor. */
public record ChatMessageHistoryResponse(
        List<ChatMessageResponse> content,
        boolean hasMore,
        Long nextBeforeMessageId
) {}
