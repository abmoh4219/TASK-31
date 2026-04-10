package com.meridian.retail.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MEDIUM #6: verify the RateLimitFilter emits HTTP 429 with a Retry-After header once
 * the per-user bucket is drained. Uses a low per-minute cap so the test runs fast, then
 * hammers the filter with sequential requests under the same authenticated principal.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // Low caps so the test drains the bucket quickly. Standard = 5, export = 2.
        ReflectionTestUtils.setField(filter, "standardLimit", 5L);
        ReflectionTestUtils.setField(filter, "exportLimit", 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void standardBucketReturns429AfterExhaustion() throws Exception {
        // 5 permitted requests, then the 6th is rejected.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/campaigns");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200); // default
        }
        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        MockHttpServletRequest overflow = new MockHttpServletRequest("GET", "/campaigns");
        MockHttpServletResponse overflowRes = new MockHttpServletResponse();
        filter.doFilter(overflow, overflowRes, chain);

        assertThat(overflowRes.getStatus()).isEqualTo(429);
        assertThat(overflowRes.getHeader("Retry-After")).isEqualTo("60");
        assertThat(overflowRes.getContentType()).contains("application/json");
        assertThat(overflowRes.getContentAsString()).contains("Rate limit exceeded");
        // The overflow request must NOT have been forwarded down the chain.
        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exportBucketHasTighterLimit() throws Exception {
        // 2 allowed, 3rd rejected on /analytics/export/**.
        for (int i = 0; i < 2; i++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/analytics/export/csv"),
                    new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse third = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/analytics/export/csv"), third, chain);

        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void standardAndExportBucketsAreIndependent() throws Exception {
        // Drain the export bucket; standard should still be usable.
        for (int i = 0; i < 2; i++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/analytics/export/csv"),
                    new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse exportOverflow = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/analytics/export/csv"), exportOverflow, chain);
        assertThat(exportOverflow.getStatus()).isEqualTo(429);

        MockHttpServletResponse standard = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/campaigns"), standard, chain);
        assertThat(standard.getStatus()).isEqualTo(200);
    }
}
