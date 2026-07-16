package com.softmatrix.portal.rbac;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.rbac.dto.RoleRequest;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoleServiceTest {

    RoleRepository repo = mock(RoleRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    RoleService service;

    UUID builtInId = UUID.randomUUID();
    UUID customId = UUID.randomUUID();
    RoleEntity builtIn = new RoleEntity();
    RoleEntity custom = new RoleEntity();

    @BeforeEach
    void setUp() {
        builtIn.setId(builtInId);
        builtIn.setName("Platform Admin");
        builtIn.setBuiltIn(true);
        custom.setId(customId);
        custom.setName("运营");
        custom.setBuiltIn(false);
        when(repo.findById(builtInId)).thenReturn(Optional.of(builtIn));
        when(repo.findById(customId)).thenReturn(Optional.of(custom));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new RoleService(repo, users);
    }

    @Test
    void create_validates_permission_codes() {
        assertThatThrownBy(() -> service.create(new RoleRequest("r", null, Set.of("NOT_A_PERM"))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PERMISSION");
    }

    @Test
    void create_duplicate_name_rejected() {
        when(repo.existsByName("运营")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new RoleRequest("运营", null, Set.of("AGENT_VIEW"))))
                .hasFieldOrPropertyWithValue("code", "ROLE_NAME_TAKEN");
    }

    @Test
    void create_custom_role_ok() {
        when(repo.existsByName("运营")).thenReturn(false);
        var res = service.create(new RoleRequest("运营", "desc", Set.of("AGENT_VIEW", "AGENT_RUN")));
        assertThat(res.builtIn()).isFalse();
        assertThat(res.permissions()).containsExactlyInAnyOrder("AGENT_VIEW", "AGENT_RUN");
    }

    @Test
    void update_or_delete_built_in_rejected() {
        assertThatThrownBy(() -> service.update(builtInId,
                new RoleRequest("x", null, Set.of("AGENT_VIEW"))))
                .hasFieldOrPropertyWithValue("code", "ROLE_BUILT_IN");
        assertThatThrownBy(() -> service.delete(builtInId))
                .hasFieldOrPropertyWithValue("code", "ROLE_BUILT_IN");
    }

    @Test
    void delete_assigned_role_rejected() {
        when(users.countByRoles_Id(customId)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(customId))
                .hasFieldOrPropertyWithValue("code", "ROLE_IN_USE");
    }

    @Test
    void delete_unassigned_custom_role_ok() {
        when(users.countByRoles_Id(customId)).thenReturn(0L);
        service.delete(customId);
        verify(repo).delete(custom);
    }
}
