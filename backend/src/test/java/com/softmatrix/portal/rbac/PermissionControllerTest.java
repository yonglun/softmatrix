package com.softmatrix.portal.rbac;

import com.softmatrix.portal.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PermissionController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class PermissionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean(name = "perm") PermissionChecker perm;

    @Test
    void without_permission_is_403_with_code() throws Exception {
        when(perm.has("ROLE_VIEW")).thenReturn(false);
        mvc.perform(get("/api/permissions").with(oidcLogin()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void with_permission_returns_catalog() throws Exception {
        when(perm.has("ROLE_VIEW")).thenReturn(true);
        mvc.perform(get("/api/permissions").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(9))
           .andExpect(jsonPath("$[0].code").value("AGENT_VIEW"));
    }
}
