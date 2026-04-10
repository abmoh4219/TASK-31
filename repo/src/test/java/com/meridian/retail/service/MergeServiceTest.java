package com.meridian.retail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentMergeLog;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.integrity.ContentVersionService;
import com.meridian.retail.integrity.MergeService;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.ContentMergeLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    @Mock ContentItemRepository contentItemRepository;
    @Mock ContentMergeLogRepository mergeLogRepository;
    @Mock ContentVersionService contentVersionService;
    @Mock AuditLogService auditLogService;
    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks MergeService svc;

    @Test
    void mergeFlipsDuplicatesToMerged() {
        ContentItem master = ContentItem.builder().id(1L).status(ContentStatus.ACTIVE).build();
        ContentItem dup1   = ContentItem.builder().id(2L).status(ContentStatus.ACTIVE).build();
        ContentItem dup2   = ContentItem.builder().id(3L).status(ContentStatus.ACTIVE).build();
        when(contentItemRepository.findById(1L)).thenReturn(Optional.of(master));
        when(contentItemRepository.findById(2L)).thenReturn(Optional.of(dup1));
        when(contentItemRepository.findById(3L)).thenReturn(Optional.of(dup2));
        when(contentItemRepository.save(any(ContentItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mergeLogRepository.save(any(ContentMergeLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        svc.merge(1L, List.of(2L, 3L), "reviewer", "127.0.0.1");

        assertThat(dup1.getStatus()).isEqualTo(ContentStatus.MERGED);
        assertThat(dup1.getMasterId()).isEqualTo(1L);
        assertThat(dup2.getStatus()).isEqualTo(ContentStatus.MERGED);
        assertThat(dup2.getMasterId()).isEqualTo(1L);

        // Snapshots taken before mutation, one per duplicate
        verify(contentVersionService, times(2)).snapshotCurrent(any(Long.class), any(String.class));
        // Merge log persisted
        verify(mergeLogRepository).save(any(ContentMergeLog.class));
    }

    @Test
    void rejectsMasterInDuplicateList() {
        assertThatThrownBy(() -> svc.merge(1L, List.of(1L, 2L), "u", "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("master cannot be");
    }

    @Test
    void rejectsEmptyDuplicateList() {
        assertThatThrownBy(() -> svc.merge(1L, List.of(), "u", "ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
