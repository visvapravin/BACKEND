package com.forumx.platform.auth.controller;

import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.common.dto.ApiResponse;
import com.forumx.platform.auth.dto.PlatformLoginRequest;
import com.forumx.platform.auth.service.PlatformAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {
 private final PlatformAuthenticationService service;
 @PostMapping("/login") public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody PlatformLoginRequest request, HttpServletRequest http) {
  return ResponseEntity.ok(ApiResponse.success("Platform login successful", service.login(request, http)));
 }
}
