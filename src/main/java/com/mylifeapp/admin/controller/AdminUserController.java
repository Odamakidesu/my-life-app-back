package com.mylifeapp.admin.controller;

import com.mylifeapp.admin.dto.EnabledUpdateRequest;
import com.mylifeapp.admin.dto.RoleUpdateRequest;
import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.common.web.BadRequestException;
import com.mylifeapp.user.dto.UserResponse;
import com.mylifeapp.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** ユーザー一覧（ID の昇順）。総件数は X-Total-Count ヘッダに載せる（メモの一覧と同じ形）。 */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        List<UserResponse> body = userService.findPage(page, size).stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(userService.count()))
                .body(body);
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody RoleUpdateRequest request) {
        requireNotSelf(principal, id);
        // 権限変更は監査対象。誰が何をしたかを追えるようにする。
        log.info("admin_role_changed actorUserId={} targetUserId={} newRole={}",
                principal.getUserId(), id, request.role());
        return UserResponse.from(userService.changeRole(id, request.role()));
    }

    @PutMapping("/{id}/enabled")
    public UserResponse changeEnabled(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody EnabledUpdateRequest request) {
        requireNotSelf(principal, id);
        log.info("admin_enabled_changed actorUserId={} targetUserId={} enabled={}",
                principal.getUserId(), id, request.enabled());
        return UserResponse.from(userService.changeEnabled(id, request.enabled()));
    }

    /**
     * 自分自身の降格・無効化は受け付けない。管理者が1人のときに誤って実行すると、
     * この API では誰も管理者に戻せなくなる（DB を直接直すしかない）。
     */
    private static void requireNotSelf(UserPrincipal principal, Long targetId) {
        if (targetId.equals(principal.getUserId())) {
            throw new BadRequestException("自分自身の権限や状態は変更できません。");
        }
    }
}
