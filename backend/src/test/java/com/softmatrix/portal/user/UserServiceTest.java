package com.softmatrix.portal.user;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.keycloak.KeycloakAdminClient;
import com.softmatrix.portal.org.DepartmentRepository;
import com.softmatrix.portal.org.PositionRepository;
import com.softmatrix.portal.rbac.RoleEntity;
import com.softmatrix.portal.rbac.RoleRepository;
import com.softmatrix.portal.user.dto.UserCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    AppUserRepository repo = mock(AppUserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    DepartmentRepository departments = mock(DepartmentRepository.class);
    PositionRepository positions = mock(PositionRepository.class);
    KeycloakAdminClient kc = mock(KeycloakAdminClient.class);
    UserService service;

    UUID adminRoleId = UUID.randomUUID();
    RoleEntity adminRole = new RoleEntity();

    @BeforeEach
    void setUp() {
        adminRole.setId(adminRoleId);
        adminRole.setName("Platform Admin");
        adminRole.setBuiltIn(true);
        when(roles.findAllById(any())).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new UserService(repo, roles, departments, positions, kc);
    }

    private AppUserEntity stored(String username, RoleEntity... rs) {
        AppUserEntity u = new AppUserEntity();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setKeycloakId("kc-" + username);
        for (RoleEntity r : rs) u.getRoles().add(r);
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void create_writes_keycloak_first_then_saves_mirror() {
        when(kc.createUser("alice", "Alice", "a@x.com", "Passw0rd!")).thenReturn("kc-123");

        var res = service.create(new UserCreateRequest(
                "alice", "Alice", "a@x.com", "Passw0rd!", null, null, Set.of()));

        assertThat(res.username()).isEqualTo("alice");
        verify(kc).createUser("alice", "Alice", "a@x.com", "Passw0rd!");
        verify(repo).save(any());
    }

    @Test
    void create_kc_failure_prevents_db_write() {
        when(kc.createUser(any(), any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已存在"));

        assertThatThrownBy(() -> service.create(new UserCreateRequest(
                "dup", null, null, "Passw0rd!", null, null, Set.of())))
                .hasFieldOrPropertyWithValue("code", "USERNAME_TAKEN");
        verify(repo, never()).save(any());
    }

    @Test
    void disable_self_rejected() {
        AppUserEntity me = stored("admin", adminRole);

        assertThatThrownBy(() -> service.setEnabled(me.getId(), false, "admin"))
                .hasFieldOrPropertyWithValue("code", "SELF_LOCKOUT");
        verify(kc, never()).setEnabled(any(), anyBoolean());
    }

    @Test
    void disable_other_user_writes_through() {
        AppUserEntity bob = stored("bob");

        service.setEnabled(bob.getId(), false, "admin");

        verify(kc).setEnabled("kc-bob", false);
        verify(repo).save(bob);
        assertThat(bob.isEnabled()).isFalse();
    }

    @Test
    void removing_own_platform_admin_rejected() {
        AppUserEntity me = stored("admin", adminRole);

        assertThatThrownBy(() -> service.setRoles(me.getId(), Set.of(), "admin"))
                .hasFieldOrPropertyWithValue("code", "SELF_LOCKOUT");
    }

    @Test
    void set_roles_for_other_user_ok() {
        AppUserEntity bob = stored("bob");
        when(roles.findAllById(Set.of(adminRoleId))).thenReturn(List.of(adminRole));

        service.setRoles(bob.getId(), Set.of(adminRoleId), "admin");

        assertThat(bob.getRoles()).containsExactly(adminRole);
    }

    @Test
    void reset_password_delegates_to_keycloak() {
        AppUserEntity bob = stored("bob");

        service.resetPassword(bob.getId(), "NewPassw0rd!");

        verify(kc).resetPassword("kc-bob", "NewPassw0rd!");
    }
}
