package com.softmatrix.portal.rbac;

import com.softmatrix.portal.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @PreAuthorize("@perm.has('…')") 的判权入口。
 * 每请求从库读取当前用户全部角色的权限并集(即时生效);
 * 同一请求内经 request attribute 记忆化,只查一次库。
 */
@Component("perm")
public class PermissionChecker {

    private final AppUserRepository users;

    public PermissionChecker(AppUserRepository users) {
        this.users = users;
    }

    public boolean has(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String username = usernameOf(auth);
        if (username == null) return false;
        return loadPermissions(username).contains(permission);
    }

    /** 当前用户全部权限并集;/api/me 也用它下发前端。 */
    public Set<String> loadPermissions(String username) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        String key = "portal.permissions." + username;
        if (attrs != null) {
            @SuppressWarnings("unchecked")
            Set<String> cached = (Set<String>) attrs.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) return cached;
        }
        // 已停用用户立即失去全部权限(门户会话即便还在,所有 API 也会 403)
        Set<String> perms = users.findByUsername(username)
                .filter(com.softmatrix.portal.user.AppUserEntity::isEnabled)
                .map(u -> u.getRoles().stream()
                        .flatMap(r -> r.getPermissions().stream())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        if (attrs != null) attrs.setAttribute(key, perms, RequestAttributes.SCOPE_REQUEST);
        return perms;
    }

    /** 生产环境 principal 是 OidcUser(取 preferred_username);单测回落 getName()。 */
    private String usernameOf(Authentication auth) {
        if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getPreferredUsername() != null) {
            return oidc.getPreferredUsername();
        }
        return auth.getName();
    }
}
