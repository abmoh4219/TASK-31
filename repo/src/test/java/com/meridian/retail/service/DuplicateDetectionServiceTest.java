package com.meridian.retail.service;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.integrity.DuplicateDetectionService;
import com.meridian.retail.integrity.FingerprintService;
import com.meridian.retail.repository.ContentItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock ContentItemRepository repo;
    @InjectMocks DuplicateDetectionService svc;

    @Test
    void exactDuplicateFoundBySha() {
        ContentItem a = ContentItem.builder().id(1L).sha256Fingerprint("abc").build();
        ContentItem b = ContentItem.builder().id(2L).sha256Fingerprint("abc").build();
        when(repo.findBySha256Fingerprint("abc")).thenReturn(List.of(a, b));
        assertThat(svc.findExactDuplicates("abc")).hasSize(2);
    }

    @Test
    void groupsClusterDuplicates() {
        // Real fingerprint service so the actual Hamming math is exercised.
        FingerprintService fs = new FingerprintService();
        ReflectionTestUtils.setField(svc, "fingerprintService", fs);

        // Two items with IDENTICAL text -> exact SHA + identical SimHash, definitely cluster.
        String sharedText = "Spring sale fifteen percent off all storewide items code SPRING15";
        String differentText = "Annual financial report Q4 earnings dividend distribution shareholder equity";

        long sharedSimHash = fs.computeSimHash(sharedText);
        String sharedSha = fs.computeSha256(sharedText);

        ContentItem a = ContentItem.builder().id(1L).simHash(sharedSimHash)
                .status(ContentStatus.ACTIVE).sha256Fingerprint(sharedSha).build();
        ContentItem b = ContentItem.builder().id(2L).simHash(sharedSimHash)
                .status(ContentStatus.ACTIVE).sha256Fingerprint(sharedSha).build();
        ContentItem c = ContentItem.builder().id(3L)
                .simHash(fs.computeSimHash(differentText))
                .status(ContentStatus.ACTIVE)
                .sha256Fingerprint(fs.computeSha256(differentText)).build();

        when(repo.findByStatus(ContentStatus.ACTIVE)).thenReturn(List.of(a, b, c));

        var groups = svc.groupDuplicates();
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).master().getId()).isEqualTo(1L);
        assertThat(groups.get(0).duplicates()).extracting(d -> d.item().getId()).contains(2L);
        assertThat(groups.get(0).duplicates().get(0).exact()).isTrue();
    }
}
