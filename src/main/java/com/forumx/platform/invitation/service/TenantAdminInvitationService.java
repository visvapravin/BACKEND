package com.forumx.platform.invitation.service;

import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.CreateTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.TenantAdminInvitationResponse;
import com.forumx.platform.invitation.dto.ValidateTenantAdminInvitationResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface TenantAdminInvitationService {
    TenantAdminInvitationResponse createInvitation(Long tenantId, CreateTenantAdminInvitationRequest request);
    ValidateTenantAdminInvitationResponse validateInvitation(String rawToken);
    LoginResponse acceptInvitation(AcceptTenantAdminInvitationRequest request, HttpServletRequest servletRequest);
}
