package com.softmatrix.portal.org;

import com.softmatrix.portal.config.SecurityConfig;
import com.softmatrix.portal.rbac.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DepartmentController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class DepartmentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean DepartmentService service;
    @MockBean(name = "perm") PermissionChecker perm;
    @MockBean com.softmatrix.portal.auth.CustomOidcUserService oidcUserService;

    @Test
    void tree_requires_org_view() throws Exception {
        when(perm.has("ORG_VIEW")).thenReturn(false);
        mvc.perform(get("/api/departments").with(oidcLogin()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void tree_returns_nodes_with_permission() throws Exception {
        when(perm.has("ORG_VIEW")).thenReturn(true);
        when(service.tree()).thenReturn(List.of());
        mvc.perform(get("/api/departments").with(oidcLogin()))
           .andExpect(status().isOk());
    }
}
