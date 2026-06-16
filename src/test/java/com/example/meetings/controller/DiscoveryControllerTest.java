package com.example.meetings.controller;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiscoveryControllerTest {

    private final DiscoveryService discoveryService = mock(DiscoveryService.class);
    private final MeetingService meetingService = mock(MeetingService.class);
    private final UserService userService = mock(UserService.class);
    private final DiscoveryController controller =
            new DiscoveryController(discoveryService, meetingService, userService);

    private static EventProvider provider(boolean configured) {
        return new EventProvider() {
            @Override
            public String name() {
                return configured ? "On" : "Off";
            }

            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public List<DiscoveredEvent> search(String query) {
                return List.of();
            }
        };
    }

    private static org.springframework.security.core.userdetails.User principal() {
        return new org.springframework.security.core.userdetails.User(
                "alice",
                "password",
                List.of());
    }

    @Test
    void discoverWithoutQueryShowsNoResults() {
        EventProvider provider = provider(false);
        when(discoveryService.providers()).thenReturn(List.of(provider));
        Model model = new ExtendedModelMap();

        String view = controller.discover(null, model);

        assertThat(view).isEqualTo("discover");
        assertThat(model.asMap())
                .containsEntry("providers", List.of(provider))
                .containsEntry("anyConfigured", false)
                .containsEntry("q", "")
                .containsEntry("results", List.of());
        verify(discoveryService, never()).search(anyString());
    }

    @Test
    void discoverWithBlankQueryDoesNotSearchEvenWhenProviderIsConfigured() {
        when(discoveryService.providers()).thenReturn(List.of(provider(true)));
        Model model = new ExtendedModelMap();

        controller.discover(" ", model);

        assertThat(model.asMap())
                .containsEntry("anyConfigured", true)
                .containsEntry("q", " ")
                .containsEntry("results", List.of());
        verify(discoveryService, never()).search(anyString());
    }

    @Test
    void discoverWithQueryDoesNotSearchWhenNoProviderIsConfigured() {
        when(discoveryService.providers()).thenReturn(List.of(provider(false)));
        Model model = new ExtendedModelMap();

        controller.discover("jazz", model);

        assertThat(model.asMap())
                .containsEntry("anyConfigured", false)
                .containsEntry("results", List.of());
        verify(discoveryService, never()).search(anyString());
    }

    @Test
    void discoverWithQueryAndConfiguredProviderAddsSearchResults() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Test",
                "1",
                "Concert",
                null,
                Instant.parse("2026-06-01T20:00:00Z"),
                null,
                "https://events.test/1",
                null);
        when(discoveryService.providers()).thenReturn(List.of(provider(false), provider(true)));
        when(discoveryService.search("jazz")).thenReturn(List.of(event));
        Model model = new ExtendedModelMap();

        controller.discover("jazz", model);

        assertThat(model.asMap())
                .containsEntry("anyConfigured", true)
                .containsEntry("q", "jazz")
                .containsEntry("results", List.of(event));
    }

    @Test
    void copyWithBlankEndCreatesEventWithoutEndTime() {
        User user = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(user);

        String view = controller.copy(
                principal(),
                "Ticketmaster",
                "tm-1",
                "Concert",
                null,
                "2026-06-01T20:00:00Z",
                " ",
                null,
                null);

        assertThat(view).isEqualTo("redirect:/calendar");
        ArgumentCaptor<DiscoveredEvent> event = ArgumentCaptor.forClass(DiscoveredEvent.class);
        verify(meetingService).copyFromDiscovered(eq(user), event.capture());
        assertThat(event.getValue().end()).isNull();
    }

    @Test
    void copyWithProvidedEndParsesEndTime() {
        User user = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(user);

        controller.copy(
                principal(),
                "SeatGeek",
                "sg-1",
                "Match",
                "Friendly",
                "2026-06-01T20:00:00Z",
                "2026-06-01T22:30:00Z",
                "https://events.test/sg-1",
                "Stadium");

        ArgumentCaptor<DiscoveredEvent> event = ArgumentCaptor.forClass(DiscoveredEvent.class);
        verify(meetingService).copyFromDiscovered(eq(user), event.capture());
        assertThat(event.getValue()).satisfies(discovered -> {
            assertThat(discovered.end()).isEqualTo(Instant.parse("2026-06-01T22:30:00Z"));
            assertThat(discovered.description()).isEqualTo("Friendly");
            assertThat(discovered.url()).isEqualTo("https://events.test/sg-1");
            assertThat(discovered.venue()).isEqualTo("Stadium");
        });
    }
}
