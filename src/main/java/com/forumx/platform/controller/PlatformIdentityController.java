package com.forumx.platform.controller;

import com.forumx.common.dto.ApiResponse;
import com.forumx.security.model.CustomUserDetails;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/platform")
public class PlatformIdentityController {
 @GetMapping("/me") @PreAuthorize("hasRole('PLATFORM_ADMIN')")
 public ResponseEntity<ApiResponse<Map<String,Object>>> me(@AuthenticationPrincipal CustomUserDetails user) {
  return ResponseEntity.ok(ApiResponse.success("Platform identity", Map.of("userId", user.getUserId(), "username", user.getUsername(), "email", user.getUser().getEmail(), "roles", user.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_")).toList(), "scope", "PLATFORM")));
 }
}
