package com.softmatrix.portal.user;

import com.softmatrix.portal.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('ORG_VIEW')")
    public List<UserResponse> list(@RequestParam(required = false) UUID dept,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Boolean enabled) {
        return service.list(dept, keyword, enabled);
    }

    @PostMapping
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public UserResponse setEnabled(@PathVariable UUID id, @Valid @RequestBody EnabledRequest req,
                                   @AuthenticationPrincipal OidcUser actor) {
        return service.setEnabled(id, req.enabled(), actor.getPreferredUsername());
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest req) {
        service.resetPassword(id, req.password());
    }

    /** 用户角色分配按权限目录属 ROLE_MANAGE(设计文档 §6 的唯一例外)。 */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@perm.has('ROLE_MANAGE')")
    public UserResponse setRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest req,
                                 @AuthenticationPrincipal OidcUser actor) {
        return service.setRoles(id, req.roleIds(), actor.getPreferredUsername());
    }
}
