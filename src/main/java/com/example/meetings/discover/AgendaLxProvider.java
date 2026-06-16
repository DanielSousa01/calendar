package com.example.meetings.discover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lisbon's "Agenda Cultural" via the AgendaLx WordPress REST API. The endpoint is referenced
 * from dados.gov.pt (Município de Lisboa dataset) and exposes ~thousands of curated cultural
 * events — concerts, exhibitions, theatre, guided tours — that you won't find on Ticketmaster.
 * <p>
 * No API key required, but the host blocks requests with default UAs (returns RST), so we send
 * a browser-style User-Agent. Times are only partially structured (string_times like "qua: 21h30");
 * we parse the first hh'h'mm match and fall back to 20:00 Europe/Lisbon when nothing parses.
 */
@Component
public class AgendaLxProvider implements EventProvider {

    private static final Logger log = LoggerFactory.getLogger(AgendaLxProvider.class);
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})h(\\d{0,2})");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final RestClient http;

    public AgendaLxProvider() {
        this.http = RestClient.builder()
                .baseUrl("https://www.agendalx.pt/wp-json/agendalx/v1")
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (compatible; meetings-app/0.1; +http://localhost)")
                .build();
    }

    private static LocalDate nextOccurrence(List<String> occurences) {
        if (occurences == null || occurences.isEmpty()) return null;
        LocalDate today = LocalDate.now(LISBON);
        for (String s : occurences) {
            try {
                LocalDate d = LocalDate.parse(s);
                if (!d.isBefore(today)) return d;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static java.util.Optional<LocalTime> parseTime(String stringTimes) {
        if (stringTimes == null || stringTimes.isBlank()) return java.util.Optional.empty();
        Matcher m = TIME.matcher(stringTimes);
        if (!m.find()) return java.util.Optional.empty();
        try {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2));
            if (h < 0 || h > 23 || min < 0 || min > 59) return java.util.Optional.empty();
            return java.util.Optional.of(LocalTime.of(h, min));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private static String firstVenueName(Map<String, AlxVenue> venue) {
        if (venue == null || venue.isEmpty()) return null;
        AlxVenue first = venue.values().iterator().next();
        return first != null ? first.name : null;
    }

    private static String joinDescription(List<String> parts) {
        if (parts == null || parts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null) continue;
            sb.append(HTML_TAG.matcher(s).replaceAll(" ").trim()).append("\n");
        }
        String joined = sb.toString().trim();
        // Keep the description bounded — these can run very long.
        return joined.length() > 600 ? joined.substring(0, 600) + "…" : joined;
    }

    @Override
    public String name() {
        return "Agenda Cultural de Lisboa";
    }

    @Override
    public boolean isConfigured() {
        return true;
    } // public endpoint, no creds

    @Override
    public List<DiscoveredEvent> search(String query) {
        String path = UriComponentsBuilder.fromPath("/events")
                .queryParam("search", query)
                .queryParam("per_page", 20)
                .toUriString();
        try {
            List<AlxEvent> raw = http.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) ->
                            log.warn("AgendaLx search failed: {}", res.getStatusCode()))
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (raw == null) return List.of();
            LocalTime fallback = LocalTime.of(20, 0);
            List<DiscoveredEvent> results = new ArrayList<>();
            for (AlxEvent e : raw) {
                LocalDate date = nextOccurrence(e.occurences);
                if (date == null) continue; // all dates in the past
                LocalTime time = parseTime(e.stringTimes).orElse(fallback);
                String title = e.title != null ? e.title.rendered : null;
                if (title == null || title.isBlank()) continue;
                results.add(new DiscoveredEvent(
                        name(),
                        String.valueOf(e.id),
                        title,
                        joinDescription(e.description),
                        date.atTime(time).atZone(LISBON).toInstant(),
                        null,
                        e.link,
                        firstVenueName(e.venue)));
            }
            return results;
        } catch (Exception ex) {
            log.warn("AgendaLx search threw", ex);
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AlxEvent {
        public long id;
        public AlxTitle title;
        public List<String> description;
        public List<String> occurences;
        @com.fasterxml.jackson.annotation.JsonProperty("string_times")
        public String stringTimes;
        public String link;
        public Map<String, AlxVenue> venue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AlxTitle {
        public String rendered;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AlxVenue {
        public String name;
    }
}
