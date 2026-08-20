package com.forumx.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMembershipResponse {
    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private List<String> roles;
    private boolean active;
}
