package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MeetingServiceTest {

    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MeetingParticipantRepository participantRepository = mock(MeetingParticipantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MeetingService service = new MeetingService(meetingRepository, participantRepository, userRepository);

    @Test
    void proposeAutoAcceptsOrganizerAndAddsUniquePendingInvitees() {
        User organizer = new User("alice", "alice@example.com", "hash");
        User bob = new User("bob", "bob@example.com", "hash");
        Instant start = Instant.parse("2026-06-01T10:00:00Z");
        Instant end = Instant.parse("2026-06-01T11:00:00Z");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.findOverlapping(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting meeting = service.propose(
                organizer,
                "Planning",
                "Quarterly planning",
                start,
                end,
                Arrays.asList(" bob ", "bob", "alice", "", null));

        assertThat(meeting.getTitle()).isEqualTo("Planning");
        assertThat(meeting.getParticipants()).hasSize(2);
        assertThat(meeting.getParticipants())
                .extracting(participant -> participant.getUser().getUsername() + ":" + participant.getStatus())
                .containsExactlyInAnyOrder("alice:ACCEPTED", "bob:PENDING");
        assertThat(meeting.isConfirmed()).isFalse();
        verify(meetingRepository).save(meeting);
    }

    @Test
    void proposeRejectsOrganizerOverlapBeforeSaving() {
        User organizer = new User("alice", "alice@example.com", "hash");
        Instant start = Instant.parse("2026-06-01T10:00:00Z");
        Instant end = Instant.parse("2026-06-01T11:00:00Z");
        Meeting existing = new Meeting("Existing", "", start, end, organizer);

        when(meetingRepository.findOverlapping(organizer, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.propose(organizer, "Overlap", "", start, end, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User has an overlapping meeting: alice");
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void proposeRejectsInviteeOverlapBeforeSaving() {
        User organizer = new User("alice", "alice@example.com", "hash");
        User bob = new User("bob", "bob@example.com", "hash");
        Instant start = Instant.parse("2026-06-01T10:00:00Z");
        Instant end = Instant.parse("2026-06-01T11:00:00Z");
        Meeting existing = new Meeting("Existing", "", start, end, bob);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.findOverlapping(organizer, start, end)).thenReturn(List.of());
        when(meetingRepository.findOverlapping(bob, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.propose(organizer, "Overlap", "", start, end, List.of("bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User has an overlapping meeting: bob");
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void proposeRejectsInvalidTimeWindow() {
        User organizer = new User("alice", "alice@example.com", "hash");
        Instant start = Instant.parse("2026-06-01T10:00:00Z");

        assertThatThrownBy(() -> service.propose(organizer, "Bad", "", start, start, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End time must be after start time");
    }

    @Test
    void proposeRejectsUnknownInvitee() {
        User organizer = new User("alice", "alice@example.com", "hash");
        Instant start = Instant.parse("2026-06-01T10:00:00Z");
        Instant end = Instant.parse("2026-06-01T11:00:00Z");

        when(meetingRepository.findOverlapping(organizer, start, end)).thenReturn(List.of());
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose(organizer, "Planning", "", start, end, List.of("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown invitee: missing");
    }

    @Test
    void respondOnlyAllowsAcceptedOrDeclined() {
        User user = new User("bob", "bob@example.com", "hash");

        assertThatThrownBy(() -> service.respond(1L, user, InviteStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Response must be ACCEPTED or DECLINED");
    }

    @Test
    void respondUpdatesInviteStatus() {
        User organizer = new User("alice", "alice@example.com", "hash");
        User invitee = new User("bob", "bob@example.com", "hash");
        Meeting meeting = new Meeting(
                "Planning",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);

        when(participantRepository.findByMeetingIdAndUserId(7L, invitee.getId())).thenReturn(Optional.of(participant));

        service.respond(7L, invitee, InviteStatus.ACCEPTED);

        assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void respondRejectsMissingInvite() {
        User invitee = new User("bob", "bob@example.com", "hash");

        when(participantRepository.findByMeetingIdAndUserId(7L, invitee.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(7L, invitee, InviteStatus.DECLINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No invite found for this user");
    }

    @Test
    void copyFromDiscoveredDefaultsMissingEndAndBuildsSourceDescription() {
        User user = new User("alice", "alice@example.com", "hash");
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster",
                "tm-1",
                "Concert",
                "Live show",
                Instant.parse("2026-06-01T20:00:00Z"),
                null,
                "https://example.test/event",
                "Arena");
        when(meetingRepository.findOverlapping(
                user,
                Instant.parse("2026-06-01T20:00:00Z"),
                Instant.parse("2026-06-01T22:00:00Z")))
                .thenReturn(List.of());
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting meeting = service.copyFromDiscovered(user, event);

        assertThat(meeting.getEndTime()).isEqualTo(Instant.parse("2026-06-01T22:00:00Z"));
        assertThat(meeting.getDescription()).contains("Live show", "Venue: Arena", "Source: Ticketmaster");
        assertThat(meeting.getParticipants())
                .singleElement()
                .extracting(MeetingParticipant::getStatus)
                .isEqualTo(InviteStatus.ACCEPTED);
        assertThat(meeting.isConfirmed()).isTrue();
    }

    @Test
    void copyFromDiscoveredUsesProvidedEndAndMinimalSourceDescription() {
        User user = new User("alice", "alice@example.com", "hash");
        DiscoveredEvent event = new DiscoveredEvent(
                "Agenda",
                "agenda-1",
                "Exhibition",
                null,
                Instant.parse("2026-06-01T20:00:00Z"),
                Instant.parse("2026-06-01T21:30:00Z"),
                null,
                null);
        when(meetingRepository.findOverlapping(user, event.start(), event.end())).thenReturn(List.of());
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting meeting = service.copyFromDiscovered(user, event);

        assertThat(meeting.getEndTime()).isEqualTo(Instant.parse("2026-06-01T21:30:00Z"));
        assertThat(meeting.getDescription()).isEqualTo("Source: Agenda");
    }

    @Test
    void copyFromDiscoveredRejectsUserOverlap() {
        User user = new User("alice", "alice@example.com", "hash");
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster",
                "tm-1",
                "Concert",
                null,
                Instant.parse("2026-06-01T20:00:00Z"),
                Instant.parse("2026-06-01T21:00:00Z"),
                null,
                null);
        Meeting existing = new Meeting(
                "Existing",
                "",
                Instant.parse("2026-06-01T20:30:00Z"),
                Instant.parse("2026-06-01T21:30:00Z"),
                user);
        when(meetingRepository.findOverlapping(user, event.start(), event.end())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.copyFromDiscovered(user, event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User has an overlapping meeting: alice");
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void calendarForIcalTokenLoadsUserAndDelegatesCalendarQuery() {
        User user = new User("alice", "alice@example.com", "hash");
        Meeting meeting = new Meeting(
                "Feed",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                user);

        when(userRepository.findByIcalToken("token-123")).thenReturn(Optional.of(user));
        when(meetingRepository.findCalendarMeetings(user)).thenReturn(List.of(meeting));

        assertThat(service.calendarForIcalToken("token-123")).containsExactly(meeting);
    }

    @Test
    void calendarForIcalTokenRejectsInvalidToken() {
        when(userRepository.findByIcalToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calendarForIcalToken("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid iCal token");
    }
}
