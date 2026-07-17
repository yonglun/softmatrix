package com.softmatrix.portal.rbac;

import com.softmatrix.portal.user.AppUserEntity;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionCheckerTest {

    AppUserRepository users = mock(AppUserRepository.class);
    PermissionChecker checker = new PermissionChecker(users);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(username, "n/a"));
    }

    private AppUserEntity userWithRoles(RoleEntity... roles) {
        AppUserEntity u = new AppUserEntity();
        u.setUsername("alice");
        for (RoleEntity r : roles) u.getRoles().add(r);
        return u;
    }

    private RoleEntity role(String... perms) {
        RoleEntity r = new RoleEntity();
        r.setPermissions(Set.of(perms));
        return r;
    }

    @Test
    void union_across_multiple_roles() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(
                userWithRoles(role("AGENT_VIEW"), role("AGENT_RUN", "ORG_VIEW"))));
        loginAs("alice");

        assertThat(checker.has("AGENT_VIEW")).isTrue();
        assertThat(checker.has("ORG_VIEW")).isTrue();
        assertThat(checker.has("ROLE_MANAGE")).isFalse();
    }

    @Test
    void disabled_user_has_no_permissions() {
        AppUserEntity u = userWithRoles(role("AGENT_VIEW"));
        u.setEnabled(false);
        when(users.findByUsername("alice")).thenReturn(Optional.of(u));
        loginAs("alice");

        assertThat(checker.has("AGENT_VIEW")).isFalse();
    }

    @Test
    void unknown_user_has_no_permissions() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        loginAs("ghost");
        assertThat(checker.has("AGENT_VIEW")).isFalse();
    }

    @Test
    void no_authentication_denies() {
        assertThat(checker.has("AGENT_VIEW")).isFalse();
    }
}
