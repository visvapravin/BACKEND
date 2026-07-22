package com.forumx.presence.dto.response;

import java.util.List;

public record OnlineUsersResponse(
        List<PresenceSummary> onlineUsers,
        int totalOnlineCount
) {}
