package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rest-api;MODE=LEGACY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.base-url=http://test.local"
})
class RestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private MeetingParticipantRepository participantRepository;

    @BeforeEach
    void cleanDatabase() {
        meetingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unauthenticatedCalendarRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void registerEndpointCreatesUserAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "apiuser")
                        .param("email", "api@example.com")
                        .param("password", "secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.existsByUsername("apiuser")).isTrue();
    }

    @Test
    void registerEndpointRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "apiuser")
                        .param("email", "api@example.com")
                        .param("password", "secret"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByUsername("apiuser")).isFalse();
    }

    @Test
    @WithMockUser(username = "apiuser")
    void authenticatedCalendarRendersUserCalendarAndIcalLink() throws Exception {
        User user = userRepository.save(new User("apiuser", "api@example.com", "hash"));
        Meeting meeting = new Meeting(
                "API planning",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                user);
        meeting.addParticipant(new MeetingParticipant(meeting, user, InviteStatus.ACCEPTED));
        meetingRepository.save(meeting);

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("API planning")))
                .andExpect(content().string(containsString("http://test.local/ical/" + user.getIcalToken() + ".ics")));
    }

    @Test
    void icalFeedIsPublicAndRendersCalendarContent() throws Exception {
        User user = userRepository.save(new User("feeduser", "feed@example.com", "hash"));
        Meeting meeting = new Meeting(
                "Feed event",
                "Visible in iCal",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                user);
        meeting.addParticipant(new MeetingParticipant(meeting, user, InviteStatus.ACCEPTED));
        meetingRepository.save(meeting);

        mockMvc.perform(get("/ical/" + user.getIcalToken() + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("SUMMARY:Feed event")));
    }

    @Test
    void invalidIcalTokenReturnsNotFound() throws Exception {
        mockMvc.perform(get("/ical/not-a-real-token.ics"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "bob")
    @Tag("bug")
    void respondAcceptsOnlyKnownActions() throws Exception {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        Meeting meeting = meeting("Invite", alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));
        meeting = meetingRepository.save(meeting);

        mockMvc.perform(post("/meetings/" + meeting.getId() + "/respond")
                        .with(csrf())
                        .param("action", "maybe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "charlie")
    @Tag("bug")
    void userCannotRespondToSomeoneElsesInvite() throws Exception {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        userRepository.save(new User("charlie", "charlie@example.com", "hash"));
        Meeting meeting = meeting("Invite", alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));
        meeting = meetingRepository.save(meeting);

        mockMvc.perform(post("/meetings/" + meeting.getId() + "/respond")
                        .with(csrf())
                        .param("action", "accept"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "apiuser")
    void proposeInvalidTimeWindowRerendersFormWithError() throws Exception {
        userRepository.save(new User("apiuser", "api@example.com", "hash"));

        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Invalid")
                        .param("description", "")
                        .param("start", "2026-06-01T11:00")
                        .param("end", "2026-06-01T10:00")
                        .param("invitees", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("End time must be after start time")));

        assertThat(meetingRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "apiuser")
    void proposeRequiresCsrfToken() throws Exception {
        userRepository.save(new User("apiuser", "api@example.com", "hash"));

        mockMvc.perform(post("/meetings/new")
                        .param("title", "No CSRF")
                        .param("description", "")
                        .param("start", "2026-06-01T10:00")
                        .param("end", "2026-06-01T11:00")
                        .param("invitees", ""))
                .andExpect(status().isForbidden());

        assertThat(meetingRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "apiuser")
    void copyDiscoveredEventCreatesAcceptedCalendarEntry() throws Exception {
        userRepository.save(new User("apiuser", "api@example.com", "hash"));

        mockMvc.perform(post("/discover/copy")
                        .with(csrf())
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm-1")
                        .param("title", "Copied concert")
                        .param("description", "Imported through REST")
                        .param("start", "2026-06-01T20:00:00Z")
                        .param("end", "")
                        .param("url", "https://events.test/tm-1")
                        .param("venue", "Arena"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        assertThat(meetingRepository.findAll()).singleElement().satisfies(meeting -> {
            assertThat(meeting.getTitle()).isEqualTo("Copied concert");
            assertThat(meeting.getEndTime()).isEqualTo(Instant.parse("2026-06-01T22:00:00Z"));
        });
        assertThat(participantRepository.findAll())
                .singleElement()
                .extracting(MeetingParticipant::getStatus)
                .isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = "apiuser")
    void copyDiscoveredEventRequiresCsrfToken() throws Exception {
        userRepository.save(new User("apiuser", "api@example.com", "hash"));

        mockMvc.perform(post("/discover/copy")
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm-1")
                        .param("title", "Copied concert")
                        .param("start", "2026-06-01T20:00:00Z"))
                .andExpect(status().isForbidden());

        assertThat(meetingRepository.findAll()).isEmpty();
    }

    private static Meeting meeting(String title, User organizer) {
        return new Meeting(
                title,
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                organizer);
    }
}
