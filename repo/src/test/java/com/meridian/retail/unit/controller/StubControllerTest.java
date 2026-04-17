package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.StubController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubControllerTest {

    private final StubController controller = new StubController();

    @Test
    void uploadLandingRedirectsToFilesUpload() {
        String view = controller.uploadLanding();
        assertThat(view).isEqualTo("redirect:/files/upload");
    }
}
