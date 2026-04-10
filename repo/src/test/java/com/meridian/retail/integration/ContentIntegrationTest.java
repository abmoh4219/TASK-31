package com.meridian.retail.integration;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.integrity.ContentImportService;
import com.meridian.retail.integrity.ContentVersionService;
import com.meridian.retail.integrity.DuplicateDetectionService;
import com.meridian.retail.integrity.MergeService;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.ContentVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContentIntegrationTest extends AbstractIntegrationTest {

    @Autowired ContentImportService importService;
    @Autowired DuplicateDetectionService duplicateDetectionService;
    @Autowired MergeService mergeService;
    @Autowired ContentVersionService versionService;
    @Autowired ContentItemRepository contentItemRepository;
    @Autowired ContentVersionRepository contentVersionRepository;

    @Test
    void importDuplicatesMergeAndRollback() {
        // Import 3 near-duplicate content items
        ContentItem a = importService.importSingle(1L, "Spring Sale 15",
                "https://intranet.local/marketing/spring-15a.html",
                "Save fifteen percent off all spring items with code SPRING15 at the register",
                "ops", "127.0.0.1");
        ContentItem b = importService.importSingle(1L, "Spring Sale 15 v2",
                "https://intranet.local/marketing/spring-15b.html",
                "Save fifteen percent off spring items with code SPRING15 at the register",
                "ops", "127.0.0.1");
        ContentItem c = importService.importSingle(1L, "Spring Sale 15 v3",
                "https://intranet.local/marketing/spring-15c.html",
                "Save fifteen percent off all spring goods with code SPRING15 at the register today",
                "ops", "127.0.0.1");

        var groups = duplicateDetectionService.groupDuplicates();
        assertThat(groups).isNotEmpty();

        // Merge b and c into a
        mergeService.merge(a.getId(), List.of(b.getId(), c.getId()), "reviewer", "127.0.0.1");

        ContentItem reloadedB = contentItemRepository.findById(b.getId()).orElseThrow();
        ContentItem reloadedC = contentItemRepository.findById(c.getId()).orElseThrow();
        assertThat(reloadedB.getStatus()).isEqualTo(ContentStatus.MERGED);
        assertThat(reloadedC.getStatus()).isEqualTo(ContentStatus.MERGED);
        assertThat(reloadedB.getMasterId()).isEqualTo(a.getId());
        assertThat(reloadedC.getMasterId()).isEqualTo(a.getId());

        // Each merged item should have at least one version snapshot
        assertThat(contentVersionRepository.countByContentId(b.getId())).isGreaterThanOrEqualTo(1);
        assertThat(contentVersionRepository.countByContentId(c.getId())).isGreaterThanOrEqualTo(1);

        // Rollback b to its first version (which was the pre-merge state)
        var historyB = versionService.getHistory(b.getId());
        int firstVersion = historyB.stream().mapToInt(v -> v.getVersionNum()).min().orElseThrow();
        versionService.rollback(b.getId(), firstVersion, "admin", "127.0.0.1");

        // History should now contain at least 2 versions (snapshot before merge + snapshot before rollback)
        assertThat(versionService.getHistory(b.getId()).size()).isGreaterThanOrEqualTo(2);
    }
}
