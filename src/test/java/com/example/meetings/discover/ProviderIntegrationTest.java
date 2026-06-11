package com.example.meetings.discover;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderIntegrationTest {

    @Test
    void ticketmasterProviderMapsDiscoveryResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ticketmaster.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider provider = ticketmasterProvider("secret", "PT", builder.build());

        server.expect(requestTo("https://ticketmaster.test/events.json?keyword=jazz&size=20&apikey=secret&countryCode=PT"))
                .andRespond(withSuccess("""
                        {
                          "_embedded": {
                            "events": [{
                              "id": "tm-1",
                              "name": "Lisbon Jazz",
                              "url": "https://tickets.test/tm-1",
                              "info": "Late set",
                              "dates": { "start": { "dateTime": "2026-06-01T21:00:00Z" } },
                              "_embedded": { "venues": [{ "name": "Music Hall" }] }
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("jazz");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("Ticketmaster");
            assertThat(event.externalId()).isEqualTo("tm-1");
            assertThat(event.title()).isEqualTo("Lisbon Jazz");
            assertThat(event.description()).isEqualTo("Late set");
            assertThat(event.venue()).isEqualTo("Music Hall");
        });
        server.verify();
    }

    @Test
    void ticketmasterProviderEncodesQueryParameters() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ticketmaster.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider provider = ticketmasterProvider("secret key", "PT", builder.build());

        server.expect(requestTo("https://ticketmaster.test/events.json?keyword=lisbon%20jazz&size=20&apikey=secret%20key&countryCode=PT"))
                .andRespond(withSuccess("{\"_embedded\":{\"events\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(provider.search("lisbon jazz")).isEmpty();
        server.verify();
    }

    @Test
    void ticketmasterProviderSkipsUnconfiguredAndMalformedEvents() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ticketmaster.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider unconfigured = ticketmasterProvider("", "PT", builder.build());

        assertThat(unconfigured.search("jazz")).isEmpty();
        server.verify();

        server.reset();
        TicketmasterProvider configured = ticketmasterProvider("secret", "PT", builder.build());
        server.expect(requestTo("https://ticketmaster.test/events.json?keyword=jazz&size=20&apikey=secret&countryCode=PT"))
                .andRespond(withSuccess("""
                        {
                          "_embedded": {
                            "events": [
                              { "id": "missing-date", "name": "TBA", "dates": { "start": {} } },
                              { "id": "bad-date", "name": "Bad", "dates": { "start": { "dateTime": "not-an-instant" } } }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(configured.search("jazz")).isEmpty();
        server.verify();
    }

    @Test
    void ticketmasterProviderReturnsEmptyOnHttpError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ticketmaster.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider provider = ticketmasterProvider("secret", "PT", builder.build());

        server.expect(requestTo("https://ticketmaster.test/events.json?keyword=jazz&size=20&apikey=secret&countryCode=PT"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(provider.search("jazz")).isEmpty();
        server.verify();
    }

    @Test
    void seatGeekProviderMapsEventsResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://seatgeek.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeatGeekProvider provider = seatGeekProvider("client", builder.build());

        server.expect(requestTo("https://seatgeek.test/events?q=football&per_page=20&client_id=client"))
                .andRespond(withSuccess("""
                        {
                          "events": [{
                            "id": 42,
                            "title": "Portugal Match",
                            "short_title": "POR",
                            "datetime_utc": "2026-06-03T18:30:00",
                            "url": "https://tickets.test/sg-42",
                            "description": "Friendly",
                            "venue": { "name": "Stadium" }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("football");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("SeatGeek");
            assertThat(event.externalId()).isEqualTo("42");
            assertThat(event.title()).isEqualTo("Portugal Match");
            assertThat(event.venue()).isEqualTo("Stadium");
        });
        server.verify();
    }

    @Test
    void seatGeekProviderUsesShortTitleWhenTitleIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://seatgeek.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeatGeekProvider provider = seatGeekProvider("client", builder.build());

        server.expect(requestTo("https://seatgeek.test/events?q=football&per_page=20&client_id=client"))
                .andRespond(withSuccess("""
                        {
                          "events": [{
                            "id": 43,
                            "short_title": "POR v ESP",
                            "datetime_utc": "2026-06-03T18:30:00"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.search("football"))
                .singleElement()
                .extracting(DiscoveredEvent::title)
                .isEqualTo("POR v ESP");
        server.verify();
    }

    @Test
    void seatGeekProviderSkipsMalformedEventsAndReturnsEmptyOnHttpError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://seatgeek.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeatGeekProvider provider = seatGeekProvider("client", builder.build());

        server.expect(requestTo("https://seatgeek.test/events?q=football&per_page=20&client_id=client"))
                .andRespond(withSuccess("""
                        { "events": [{ "id": 1, "title": "Bad", "datetime_utc": "not-a-date" }] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.search("football")).isEmpty();
        server.verify();

        server.reset();
        server.expect(requestTo("https://seatgeek.test/events?q=football&per_page=20&client_id=client"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(provider.search("football")).isEmpty();
        server.verify();
    }

    @Test
    void agendaLxProviderMapsPublicEndpointResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://agenda.test")
                .defaultHeader("User-Agent", "test-agent");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgendaLxProvider provider = agendaLxProvider(builder.build());
        String futureDate = LocalDate.now(ZoneId.of("Europe/Lisbon")).plusDays(1).toString();

        server.expect(requestTo("https://agenda.test/events?search=theatre&per_page=20"))
                .andExpect(header("User-Agent", "test-agent"))
                .andRespond(withSuccess("""
                        [{
                          "id": 9,
                          "title": { "rendered": "Modern Theatre" },
                          "description": ["<p>Opening night</p>"],
                          "occurences": ["%s"],
                          "string_times": "sex: 21h30",
                          "link": "https://agenda.test/event/9",
                          "venue": { "a": { "name": "Teatro" } }
                        }]
                        """.formatted(futureDate), MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("theatre");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("Agenda Cultural de Lisboa");
            assertThat(event.externalId()).isEqualTo("9");
            assertThat(event.title()).isEqualTo("Modern Theatre");
            assertThat(event.description()).isEqualTo("Opening night");
            assertThat(event.venue()).isEqualTo("Teatro");
        });
        server.verify();
    }

    @Test
    void agendaLxProviderUsesFallbackTimeWhenNoTimeIsParseable() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://agenda.test")
                .defaultHeader("User-Agent", "test-agent");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgendaLxProvider provider = agendaLxProvider(builder.build());
        LocalDate futureDate = LocalDate.now(ZoneId.of("Europe/Lisbon")).plusDays(1);

        server.expect(requestTo("https://agenda.test/events?search=expo&per_page=20"))
                .andRespond(withSuccess("""
                        [{
                          "id": 10,
                          "title": { "rendered": "Late Expo" },
                          "occurences": ["%s"],
                          "string_times": "horario a anunciar"
                        }]
                        """.formatted(futureDate), MediaType.APPLICATION_JSON));

        assertThat(provider.search("expo"))
                .singleElement()
                .extracting(DiscoveredEvent::start)
                .isEqualTo(futureDate.atTime(20, 0).atZone(ZoneId.of("Europe/Lisbon")).toInstant());
        server.verify();
    }

    @Test
    void agendaLxProviderSkipsPastOccurrencesBlankTitlesAndHandlesHttpErrors() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://agenda.test")
                .defaultHeader("User-Agent", "test-agent");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgendaLxProvider provider = agendaLxProvider(builder.build());
        String pastDate = LocalDate.now(ZoneId.of("Europe/Lisbon")).minusDays(1).toString();

        server.expect(requestTo("https://agenda.test/events?search=theatre&per_page=20"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 1,
                            "title": { "rendered": "Past" },
                            "occurences": ["%s"],
                            "string_times": "sex: 21h30"
                          },
                          {
                            "id": 2,
                            "title": { "rendered": " " },
                            "occurences": ["%s"],
                            "string_times": "sex: 21h30"
                          }
                        ]
                        """.formatted(pastDate, LocalDate.now(ZoneId.of("Europe/Lisbon")).plusDays(1)), MediaType.APPLICATION_JSON));

        assertThat(provider.search("theatre")).isEmpty();
        server.verify();

        server.reset();
        server.expect(requestTo("https://agenda.test/events?search=theatre&per_page=20"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(provider.search("theatre")).isEmpty();
        server.verify();
    }

    private static TicketmasterProvider ticketmasterProvider(String apiKey, String countryCode, RestClient http) {
        TicketmasterProvider provider = new TicketmasterProvider(apiKey, countryCode);
        ReflectionTestUtils.setField(provider, "http", http);
        return provider;
    }

    private static SeatGeekProvider seatGeekProvider(String clientId, RestClient http) {
        SeatGeekProvider provider = new SeatGeekProvider(clientId);
        ReflectionTestUtils.setField(provider, "http", http);
        return provider;
    }

    private static AgendaLxProvider agendaLxProvider(RestClient http) {
        AgendaLxProvider provider = new AgendaLxProvider();
        ReflectionTestUtils.setField(provider, "http", http);
        return provider;
    }
}
