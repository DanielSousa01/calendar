package com.example.meetings.discover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SeatGeekProvider implements EventProvider {

    private static final Logger log = LoggerFactory.getLogger(SeatGeekProvider.class);

    private final String clientId;
    private final RestClient http;

    public SeatGeekProvider(@Value("${app.discover.seatgeek.client-id:}") String clientId) {
        this.clientId = clientId;
        this.http = RestClient.builder().baseUrl("https://api.seatgeek.com/2").build();
    }

    private static Instant parseStart(SgEvent e) {
        // SeatGeek returns datetime_utc as ISO-8601 without a zone designator; treat as UTC.
        if (e.datetimeUtc == null) return null;
        try {
            return LocalDateTime.parse(e.datetimeUtc).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public String name() {
        return "SeatGeek";
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public List<DiscoveredEvent> search(String query) {
        if (!isConfigured()) return List.of();
        String path = UriComponentsBuilder.fromPath("/events")
                .queryParam("q", query)
                .queryParam("per_page", 20)
                .queryParam("client_id", clientId)
                .toUriString();
        try {
            Response body = http.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) ->
                            log.warn("SeatGeek search failed: {}", res.getStatusCode()))
                    .body(Response.class);
            if (body == null || body.events == null) return List.of();
            List<DiscoveredEvent> results = new ArrayList<>();
            for (SgEvent e : body.events) {
                Instant start = parseStart(e);
                if (start == null) continue;
                String venue = e.venue != null ? e.venue.name : null;
                String title = e.title != null ? e.title : e.shortTitle;
                results.add(new DiscoveredEvent(
                        name(), String.valueOf(e.id), title, e.description, start, null, e.url, venue));
            }
            return results;
        } catch (Exception ex) {
            log.warn("SeatGeek search threw", ex);
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Response {
        public List<SgEvent> events;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SgEvent {
        public long id;
        public String title;
        @JsonProperty("short_title")
        public String shortTitle;
        @JsonProperty("datetime_utc")
        public String datetimeUtc;
        public String url;
        public String description;
        public SgVenue venue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SgVenue {
        public String name;
    }
}
