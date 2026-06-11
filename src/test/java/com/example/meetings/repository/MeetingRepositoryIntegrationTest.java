package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MeetingRepositoryIntegrationTest {

    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Test
    void calendarQueryUsesConcreteH2DatabaseAndFiltersDeclinedInvites() {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));

        Meeting declinedForBob = new Meeting(
                "Organizer copy remains",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                alice);
        declinedForBob.addParticipant(new MeetingParticipant(declinedForBob, alice, InviteStatus.ACCEPTED));
        declinedForBob.addParticipant(new MeetingParticipant(declinedForBob, bob, InviteStatus.DECLINED));
        meetingRepository.save(declinedForBob);

        Meeting pendingForAlice = new Meeting(
                "Pending invite",
                "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T11:00:00Z"),
                bob);
        pendingForAlice.addParticipant(new MeetingParticipant(pendingForAlice, bob, InviteStatus.ACCEPTED));
        pendingForAlice.addParticipant(new MeetingParticipant(pendingForAlice, alice, InviteStatus.PENDING));
        meetingRepository.save(pendingForAlice);

        assertThat(meetingRepository.findCalendarMeetings(alice))
                .extracting(Meeting::getTitle)
                .containsExactly("Organizer copy remains", "Pending invite");
        assertThat(meetingRepository.findCalendarMeetings(bob))
                .extracting(Meeting::getTitle)
                .containsExactly("Pending invite");
        assertThat(participantRepository.findByUserAndStatus(alice, InviteStatus.PENDING))
                .singleElement()
                .extracting(participant -> participant.getMeeting().getTitle())
                .isEqualTo("Pending invite");
    }

    @Test
    void overlapQueryFindsIntersectingSlotsButAllowsTouchingBoundaries() {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        Meeting meeting = new Meeting(
                "Existing",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(meeting);

        assertThat(meetingRepository.findOverlapping(
                alice,
                Instant.parse("2026-06-01T10:30:00Z"),
                Instant.parse("2026-06-01T11:30:00Z")))
                .extracting(Meeting::getTitle)
                .containsExactly("Existing");
        assertThat(meetingRepository.findOverlapping(
                alice,
                Instant.parse("2026-06-01T09:00:00Z"),
                Instant.parse("2026-06-01T10:00:00Z")))
                .isEmpty();
        assertThat(meetingRepository.findOverlapping(
                alice,
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-06-01T12:00:00Z")))
                .isEmpty();
    }

    @Test
    void overlapQueryIgnoresDeclinedParticipantButKeepsOrganizerCopy() {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        Meeting meeting = new Meeting(
                "Declined invite",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.DECLINED));
        meetingRepository.save(meeting);

        assertThat(meetingRepository.findOverlapping(
                bob,
                Instant.parse("2026-06-01T10:30:00Z"),
                Instant.parse("2026-06-01T11:30:00Z")))
                .isEmpty();
        assertThat(meetingRepository.findOverlapping(
                alice,
                Instant.parse("2026-06-01T10:30:00Z"),
                Instant.parse("2026-06-01T11:30:00Z")))
                .extracting(Meeting::getTitle)
                .containsExactly("Declined invite");
    }

    @Test
    void calendarQueryOrdersMeetingsByStartTime() {
        User alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        Meeting later = new Meeting(
                "Later",
                "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T11:00:00Z"),
                alice);
        later.addParticipant(new MeetingParticipant(later, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(later);

        Meeting earlier = new Meeting(
                "Earlier",
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                alice);
        earlier.addParticipant(new MeetingParticipant(earlier, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(earlier);

        assertThat(meetingRepository.findCalendarMeetings(alice))
                .extracting(Meeting::getTitle)
                .containsExactly("Earlier", "Later");
    }
}
