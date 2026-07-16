package com.softmatrix.portal.user;

import com.softmatrix.portal.config.SecurityConfig;
import com.softmatrix.portal.rbac.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class UserControllerTest {

    @Autowired MockMvc mvc;
    @MockBean UserService service;
    @MockBean(name = "perm") PermissionChecker perm;
    @MockBean com.softmatrix.portal.auth.CustomOidcUserService oidcUserService;

    @Test
    void list_requires_org_view() throws Exception {
        when(perm.has("ORG_VIEW")).thenReturn(false);
        mvc.perform(get("/api/users").with(oidcLogin()))
           .andExpect(status().isForbidden());
    }

    @Test
    void set_roles_requires_role_manage_not_org_manage() throws Exception {
        when(perm.has("ORG_MANAGE")).thenReturn(true);
        when(perm.has("ROLE_MANAGE")).thenReturn(false);
        mvc.perform(put("/api/users/{id}/roles", UUID.randomUUID())
                .with(oidcLogin())
                .contentType("application/json")
                .content("{\"roleIds\":[]}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void set_roles_passes_actor_username() throws Exception {
        when(perm.has("ROLE_MANAGE")).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(service.setRoles(eq(id), any(), eq("admin"))).thenReturn(null);
        mvc.perform(put("/api/users/{id}/roles", id)
                .with(oidcLogin().idToken(t -> t.claim("preferred_username", "admin")))
                .contentType("application/json")
                .content("{\"roleIds\":[]}"))
           .andExpect(status().isOk());
    }
}
