package com.meridian.retail.integrity;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.repository.ContentItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final ContentItemRepository contentItemRepository;
    private final FingerprintService fingerprintService;

    /** A duplicate group: master candidate + the items that look like dupes of it. */
    public record DuplicateGroup(ContentItem master, List<DuplicateMatch> duplicates) {}

    /** One match in a duplicate group. */
    public record DuplicateMatch(ContentItem item, int hammingDistance, boolean exact) {}

    /** Exact-duplicate lookup by SHA-256. Returns all items sharing the fingerprint. */
    public List<ContentItem> findExactDuplicates(String sha256) {
        if (sha256 == null || sha256.isBlank()) return List.of();
        return contentItemRepository.findBySha256Fingerprint(sha256);
    }

    /**
     * Near-duplicate lookup by SimHash. We load the candidates set and do an in-memory
     * Hamming-distance scan. For an on-prem back-office tool with a few thousand items
     * this is more than fast enough; if it grows, swap in a SimHash index later.
     */
    public List<ContentItem> findNearDuplicates(long simHash) {
        List<ContentItem> active = contentItemRepository.findByStatus(ContentStatus.ACTIVE);
        List<ContentItem> result = new ArrayList<>();
        for (ContentItem item : active) {
            if (item.getSimHash() == null) continue;
            if (fingerprintService.isNearDuplicate(simHash, item.getSimHash())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Cluster all ACTIVE content items into duplicate groups. Each group has a master
     * (the lowest-id item — stable, deterministic) and the matches that fall within
     * the Hamming threshold. Items already in a group are skipped so each item appears
     * in at most one group.
     */
    public List<DuplicateGroup> groupDuplicates() {
        // Defensive copy: never mutate the list returned by the repository.
        List<ContentItem> active = new ArrayList<>(contentItemRepository.findByStatus(ContentStatus.ACTIVE));
        active.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        Set<Long> visited = new HashSet<>();
        List<DuplicateGroup> groups = new ArrayList<>();

        for (ContentItem master : active) {
            if (visited.contains(master.getId())) continue;
            if (master.getSimHash() == null) continue;

            List<DuplicateMatch> matches = new ArrayList<>();
            for (ContentItem candidate : active) {
                if (candidate.getId().equals(master.getId())) continue;
                if (visited.contains(candidate.getId())) continue;
                if (candidate.getSimHash() == null) continue;

                int dist = fingerprintService.hammingDistance(master.getSimHash(), candidate.getSimHash());
                boolean exact = master.getSha256Fingerprint() != null
                        && master.getSha256Fingerprint().equals(candidate.getSha256Fingerprint());
                if (exact || dist <= fingerprintService.nearDuplicateThreshold()) {
                    matches.add(new DuplicateMatch(candidate, dist, exact));
                    visited.add(candidate.getId());
                }
            }
            if (!matches.isEmpty()) {
                visited.add(master.getId());
                groups.add(new DuplicateGroup(master, matches));
            }
        }
        return groups;
    }
}
