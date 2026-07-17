package com.softmatrix.portal.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeController.class)
@Import({com.softmatrix.portal.config.SecurityConfig.class,
         com.softmatrix.portal.TestOAuth2Config.class})
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean(name = "perm") com.softmatrix.portal.rbac.PermissionChecker perm;
    @MockBean CustomOidcUserService oidcUserService;

    @Autowired
    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository registrations;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_redirects_to_keycloak_end_session() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout")
                .with(oidcLogin().clientRegistration(registrations.findByRegistrationId("keycloak"))))
           .andExpect(status().is3xxRedirection())
           .andExpect(header().string("Location",
                   org.hamcrest.Matchers.containsString("/protocol/openid-connect/logout")));
    }

    @Test
    void authenticated_returns_username_and_permissions() throws Exception {
        org.mockito.Mockito.when(perm.loadPermissions("admin"))
                .thenReturn(java.util.Set.of("AGENT_VIEW"));
        mvc.perform(get("/api/me").with(oidcLogin()
                .idToken(t -> t.claim("preferred_username", "admin")
                               .claim("name", "Platform Admin"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.username").value("admin"))
           .andExpect(jsonPath("$.name").value("Platform Admin"))
           .andExpect(jsonPath("$.permissions[0]").value("AGENT_VIEW"));
    }
}
