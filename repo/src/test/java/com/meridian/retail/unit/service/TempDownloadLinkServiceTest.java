package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.TempDownloadLinkRepository;
import com.meridian.retail.storage.LinkExpiredException;
import com.meridian.retail.storage.TempDownloadLinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TempDownloadLinkServiceTest {

    @Mock TempDownloadLinkRepository linkRepo;
    @Mock CampaignAttachmentRepository attachRepo;
    @InjectMocks TempDownloadLinkService svc;

    @Test
    void resolveExpiredLinkThrows() {
        TempDownloadLink link = TempDownloadLink.builder()
                .token("tok").username("alice").fileId(1L)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(linkRepo.findByToken("tok")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> svc.resolve("tok", "alice"))
                .isInstanceOf(LinkExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resolveWrongUserThrows() {
        TempDownloadLink link = TempDownloadLink.builder()
                .token("tok").username("alice").fileId(1L)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(linkRepo.findByToken("tok")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> svc.resolve("tok", "bob"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolveAlreadyUsedThrows() {
        TempDownloadLink link = TempDownloadLink.builder()
                .token("tok").username("alice").fileId(1L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(linkRepo.findByToken("tok")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> svc.resolve("tok", "alice"))
                .isInstanceOf(LinkExpiredException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resolveValidLinkReturnsResolved() {
        TempDownloadLink link = TempDownloadLink.builder()
                .token("tok").username("alice").fileId(1L)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        CampaignAttachment att = CampaignAttachment.builder()
                .id(1L).storedPath("/tmp/test.pdf").originalFilename("test.pdf").build();
        when(linkRepo.findByToken("tok")).thenReturn(Optional.of(link));
        when(linkRepo.save(any(TempDownloadLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attachRepo.findById(1L)).thenReturn(Optional.of(att));

        assertThatCode(() -> {
            var resolved = svc.resolve("tok", "alice");
            assertThat(resolved.attachment().getId()).isEqualTo(1L);
            assertThat(resolved.path().toString()).isEqualTo("/tmp/test.pdf");
        }).doesNotThrowAnyException();
    }
}
