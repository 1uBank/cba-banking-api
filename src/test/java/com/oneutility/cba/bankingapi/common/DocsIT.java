package com.oneutility.cba.bankingapi.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oneutility.cba.bankingapi.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The API reference and the contract it renders are served without a token. */
@IntegrationTest
class DocsIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void redirectsToTheDocsWithATrailingSlash() throws Exception {
        mvc.perform(get("/docs"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/docs/"));
    }

    @Test
    void servesTheDocsPage() throws Exception {
        mvc.perform(get("/docs/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/docs/index.html"));

        mvc.perform(get("/docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-url=\"../api/banking-api.yaml\"")));
    }

    @Test
    void servesTheContract() throws Exception {
        mvc.perform(get("/api/banking-api.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.1")));
    }

    @Test
    void keepsEverythingElseClosed() throws Exception {
        mvc.perform(get("/docs/other.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/other.yaml")).andExpect(status().isUnauthorized());
    }
}
