package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.CaptchaController;
import com.meridian.retail.security.LocalCaptchaService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaptchaControllerTest {

    @Mock LocalCaptchaService captchaService;
    @Mock HttpSession session;

    CaptchaController controller;

    @BeforeEach
    void setUp() {
        controller = new CaptchaController(captchaService);
    }

    @Test
    void validateWithCorrectAnswerReturnsSuccessFragment() {
        when(captchaService.validateAnswer(session, "correct")).thenReturn(true);
        String result = controller.validate("correct", session);
        assertThat(result).contains("CAPTCHA accepted");
        assertThat(result).contains("field-validation-success");
    }

    @Test
    void validateWithWrongAnswerReturnsErrorFragment() {
        when(captchaService.validateAnswer(session, "wrong")).thenReturn(false);
        String result = controller.validate("wrong", session);
        assertThat(result).contains("Incorrect");
        assertThat(result).contains("field-validation-error");
    }

    @Test
    void validateCallsCaptchaService() {
        when(captchaService.validateAnswer(any(), anyString())).thenReturn(true);
        controller.validate("test", session);
        verify(captchaService).validateAnswer(session, "test");
    }
}
