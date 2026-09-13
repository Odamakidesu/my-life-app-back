package com.mylifeapp.admin.controller;

import com.mylifeapp.admin.dto.EnabledUpdateRequest;
import com.mylifeapp.admin.dto.RoleUpdateRequest;
import com.mylifeapp.user.dto.UserResponse;
import com.mylifeapp.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者専用のユーザー操作。
 *
 * <p>登録エンドポイントから role を受け取るのをやめたため、権限の付与はここに集約される。
 * URL ルール (/api/admin/** に hasRole("ADMIN")) と @PreAuthorize の二重で守る。
 * 片方の設定ミスで開放されないようにするための多層防御であり、冗長ではない。
 *
 * <p>最初の管理者はこの API では作れない。環境構築時に DB へ直接投入する
 * （手順は docs/runbook-security-hardening.md）。
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        // 権限変更は監査対象。誰が何をしたかを追えるようにする。
        log.info("admin_role_changed targetUserId={} newRole={}", id, request.role());
        return UserResponse.from(userService.changeRole(id, request.role()));
    }

    @PutMapping("/{id}/enabled")
    public UserResponse changeEnabled(@PathVariable Long id, @Valid @RequestBody EnabledUpdateRequest request) {
        log.info("admin_enabled_changed targetUserId={} enabled={}", id, request.enabled());
        return UserResponse.from(userService.changeEnabled(id, request.enabled()));
    }
}
