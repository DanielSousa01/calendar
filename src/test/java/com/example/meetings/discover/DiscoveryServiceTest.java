package com.example.meetings.discover;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryServiceTest {

    private static EventProvider provider(boolean configured, List<DiscoveredEvent> events) {
        return new EventProvider() {
            @Override
            public String name() {
                return "Test";
            }

            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public List<DiscoveredEvent> search(String query) {
                return events;
            }
        };
    }

    private static EventProvider throwingProvider() {
        return new EventProvider() {
            @Override
            public String name() {
                return "Broken";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public List<DiscoveredEvent> search(String query) {
                throw new RuntimeException("provider unavailable");
            }
        };
    }

    private static DiscoveredEvent event(String source, String id, String url, String start) {
        return new DiscoveredEvent(source, id, source + id, null, Instant.parse(start), null, url, null);
    }

    @Test
    void searchSkipsUnconfiguredProvidersDedupesByUrlAndSortsByStart() {
        DiscoveredEvent later = event("A", "1", "https://events.test/shared", "2026-06-02T10:00:00Z");
        DiscoveredEvent duplicate = event("B", "2", "https://events.test/shared", "2026-06-01T10:00:00Z");
        DiscoveredEvent earlier = event("B", "3", null, "2026-06-01T09:00:00Z");

        DiscoveryService service = new DiscoveryService(List.of(
                provider(false, List.of(event("off", "1", null, "2026-06-01T00:00:00Z"))),
                provider(true, List.of(later)),
                provider(true, List.of(duplicate, earlier))));

        List<DiscoveredEvent> results = service.search("jazz");

        assertThat(results).containsExactly(earlier, later);
    }

    @Test
    void searchReturnsEmptyForBlankQuery() {
        DiscoveryService service = new DiscoveryService(List.of(provider(true, List.of())));

        assertThat(service.search(" ")).isEmpty();
    }

    @Test
    void searchContinuesWhenOneConfiguredProviderThrows() {
        DiscoveredEvent event = event("Healthy", "1", "https://events.test/1", "2026-06-01T09:00:00Z");
        DiscoveryService service = new DiscoveryService(List.of(
                throwingProvider(),
                provider(true, List.of(event))));

        assertThat(service.search("jazz")).containsExactly(event);
    }
}
