package com.meridian.retail.security;

import com.meridian.retail.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests — exercise the actual filter chain against a real DB.
 *
 *   - GET /campaigns anonymous   -> 302 redirect to /login
 *   - POST /login wrong password -> 302 to /login?error
 *   - POST /campaigns no CSRF    -> 403
 *   - OPS user GET /admin/...    -> 403
 */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void anonymousAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/campaigns"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void wrongPasswordRedirectsWithError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login?error**"));
    }

    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void opsUserCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "finance", roles = {"FINANCE"})
    void financeUserCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "Admin@Retail2024!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanReachAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }
}
