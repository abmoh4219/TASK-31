package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.NonceController;
import com.meridian.retail.security.RequestSigningFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NonceControllerTest {

    @Mock RequestSigningFilter requestSigningFilter;

    NonceController controller;

    @BeforeEach
    void setUp() {
        controller = new NonceController(requestSigningFilter);
    }

    @Test
    void approvalNonceReturnsNonceAndTimestamp() {
        Map<String, Object> result = controller.approvalNonce();
        assertThat(result).containsKey("nonce");
        assertThat(result).containsKey("timestamp");
        assertThat(result.get("nonce").toString()).isNotBlank();
    }

    @Test
    void adminNonceReturnsNonceAndTimestamp() {
        Map<String, Object> result = controller.adminNonce();
        assertThat(result).containsKey("nonce");
        assertThat(result).containsKey("timestamp");
    }

    @Test
    void approvalNonceGeneratesUniqueNonces() {
        Map<String, Object> r1 = controller.approvalNonce();
        Map<String, Object> r2 = controller.approvalNonce();
        assertThat(r1.get("nonce")).isNotEqualTo(r2.get("nonce"));
    }

    @Test
    void signAdminFormCallsSigningFilter() {
        when(requestSigningFilter.signFormCanonical(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("hexsig123");
        Map<String, String> req = new HashMap<>();
        req.put("method", "POST");
        req.put("path", "/admin/users");
        req.put("timestamp", "12345");
        req.put("nonce", "abc-uuid");
        Map<String, Object> result = controller.signAdminForm(req);
        assertThat(result).containsEntry("signature", "hexsig123");
    }

    @Test
    void signApprovalFormCallsSigningFilter() {
        when(requestSigningFilter.signFormCanonical(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("approvalsig456");
        Map<String, String> req = new HashMap<>();
        req.put("method", "POST");
        req.put("path", "/approval/1/approve-first");
        req.put("timestamp", "99999");
        req.put("nonce", "xyz-uuid");
        Map<String, Object> result = controller.signApprovalForm(req);
        assertThat(result).containsEntry("signature", "approvalsig456");
    }

    @Test
    void signFormWithMissingPathThrowsException() {
        Map<String, String> req = new HashMap<>();
        req.put("method", "POST");
        // path missing
        req.put("timestamp", "12345");
        req.put("nonce", "abc");
        assertThatThrownBy(() -> controller.signAdminForm(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
