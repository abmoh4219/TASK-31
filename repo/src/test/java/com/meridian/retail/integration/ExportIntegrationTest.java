package com.meridian.retail.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ExportIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = "finance", roles = {"FINANCE"})
    void financeUserCanExport() throws Exception {
        mockMvc.perform(get("/analytics/export"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void operationsCannotExport() throws Exception {
        mockMvc.perform(get("/analytics/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cs", roles = {"CUSTOMER_SERVICE"})
    void csCannotExport() throws Exception {
        mockMvc.perform(get("/analytics/export"))
                .andExpect(status().isForbidden());
    }
}
