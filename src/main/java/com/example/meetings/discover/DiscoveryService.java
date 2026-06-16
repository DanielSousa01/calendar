package com.example.meetings.discover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final List<EventProvider> providers;

    public DiscoveryService(List<EventProvider> providers) {
        this.providers = providers;
    }

    public List<EventProvider> providers() {
        return providers;
    }

    /**
     * Fans out to every configured provider and dedupes by URL. Results are sorted by start time.
     */
    public List<DiscoveredEvent> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> seenUrls = new HashSet<>();
        List<DiscoveredEvent> merged = new ArrayList<>();
        for (EventProvider p : providers) {
            if (!p.isConfigured()) continue;
            try {
                List<DiscoveredEvent> events = p.search(query);
                if (events == null) continue;
                for (DiscoveredEvent e : events) {
                    // URL is the most reliable cross-provider dedup key; fall back to source+id when missing.
                    String key = e.url() != null ? e.url() : e.source() + ":" + e.externalId();
                    if (seenUrls.add(key)) merged.add(e);
                }
            } catch (RuntimeException ex) {
                log.warn("Discovery provider {} failed: {}", p.name(), ex.getMessage());
            }
        }
        merged.sort(Comparator.comparing(DiscoveredEvent::start));
        return merged;
    }
}
