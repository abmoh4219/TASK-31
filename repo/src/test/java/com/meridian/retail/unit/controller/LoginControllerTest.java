package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.LoginController;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginControllerTest {

    @Mock Model model;
    @Mock HttpSession session;

    private LoginController controller;

    @BeforeEach
    void setUp() {
        controller = new LoginController();
    }

    @Test
    void loginWithNoParamsReturnsLoginView() {
        String view = controller.login(null, null, null, null, null, session, model);
        assertThat(view).isEqualTo("auth/login");
    }

    @Test
    void loginWithErrorParamAddsErrorMessage() {
        String view = controller.login("true", null, null, null, null, session, model);
        assertThat(view).isEqualTo("auth/login");
        verify(model).addAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void loginWithLockedParamAddsLockoutMessage() {
        String view = controller.login(null, "true", null, null, null, session, model);
        assertThat(view).isEqualTo("auth/login");
        verify(model).addAttribute(eq("errorMessage"), contains("locked"));
    }

    @Test
    void loginWithIpBlockedParamAddsBlockMessage() {
        String view = controller.login(null, null, "true", null, null, session, model);
        assertThat(view).isEqualTo("auth/login");
        verify(model).addAttribute(eq("errorMessage"), contains("blocked"));
    }

    @Test
    void loginWithCaptchaParamAddsCaptchaMessage() {
        String view = controller.login(null, null, null, "true", null, session, model);
        assertThat(view).isEqualTo("auth/login");
        verify(model).addAttribute(eq("errorMessage"), contains("CAPTCHA"));
    }

    @Test
    void loginWithLogoutParamAddsSuccessMessage() {
        String view = controller.login(null, null, null, null, "true", session, model);
        assertThat(view).isEqualTo("auth/login");
        verify(model).addAttribute(eq("successMessage"), anyString());
    }

    @Test
    void loginWithCaptchaRequiredInSessionSetsFlagOnModel() {
        when(session.getAttribute("captchaRequired")).thenReturn(Boolean.TRUE);
        controller.login(null, null, null, null, null, session, model);
        verify(model).addAttribute("captchaRequired", true);
    }

    @Test
    void loginWithoutCaptchaRequiredInSessionSetsFalseOnModel() {
        when(session.getAttribute("captchaRequired")).thenReturn(null);
        controller.login(null, null, null, null, null, session, model);
        verify(model).addAttribute("captchaRequired", false);
    }

    @Test
    void rootRedirectsToDashboard() {
        String view = controller.root();
        assertThat(view).isEqualTo("redirect:/dashboard");
    }
}
