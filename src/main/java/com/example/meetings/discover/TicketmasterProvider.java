package com.example.meetings.discover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TicketmasterProvider implements EventProvider {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterProvider.class);

    private final String apiKey;
    private final String countryCode;
    private final RestClient http;

    public TicketmasterProvider(
            @Value("${app.discover.ticketmaster.api-key:}") String apiKey,
            @Value("${app.discover.ticketmaster.country-code:PT}") String countryCode) {
        this.apiKey = apiKey;
        this.countryCode = countryCode;
        this.http = RestClient.builder()
                .baseUrl("https://app.ticketmaster.com/discovery/v2")
                .build();
    }

    private static Instant parseStart(TmEvent e) {
        if (e.dates == null || e.dates.start == null) return null;
        if (e.dates.start.dateTime != null) {
            try {
                return Instant.parse(e.dates.start.dateTime);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String name() {
        return "Ticketmaster";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<DiscoveredEvent> search(String query) {
        if (!isConfigured()) return List.of();
        try {
            Response body = http.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/events.json")
                                .queryParam("keyword", query)
                                .queryParam("size", 20)
                                .queryParam("apikey", apiKey);
                        if (countryCode != null && !countryCode.isBlank()) {
                            uriBuilder.queryParam("countryCode", countryCode);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("Ticketmaster search failed: {}", res.getStatusCode());
                    })
                    .body(Response.class);
            if (body == null || body.embedded == null || body.embedded.events == null) {
                return List.of();
            }
            List<DiscoveredEvent> results = new ArrayList<>();
            for (TmEvent e : body.embedded.events) {
                Instant start = parseStart(e);
                if (start == null) continue; // skip TBA events; they can't be calendared meaningfully
                String venue = (e.embedded != null && e.embedded.venues != null && !e.embedded.venues.isEmpty())
                        ? e.embedded.venues.get(0).name
                        : null;
                results.add(new DiscoveredEvent(
                        name(), e.id, e.name, e.info, start, null, e.url, venue));
            }
            return results;
        } catch (Exception ex) {
            log.warn("Ticketmaster search threw", ex);
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Response {
        @com.fasterxml.jackson.annotation.JsonProperty("_embedded")
        public Embedded embedded;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Embedded {
        public List<TmEvent> events;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TmEvent {
        public String id;
        public String name;
        public String url;
        public String info;
        public Dates dates;
        // Ticketmaster nests venues under another _embedded inside each event.
        @com.fasterxml.jackson.annotation.JsonProperty("_embedded")
        public EventEmbedded embedded;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EventEmbedded {
        public List<Venue> venues;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Venue {
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Dates {
        public Start start;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Start {
        public String dateTime;
    }
}
