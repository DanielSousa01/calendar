package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalServiceTest {

    private final ICalService service = new ICalService();

    @Test
    void renderEscapesIcalTextFieldsAndUsesCrlfLineEndings() {
        User owner = new User("alice,boss", "alice@example.com", "hash");
        Meeting meeting = new Meeting(
                "Plan, review; ship \\ safely",
                "Line one\nLine two; with comma, and slash \\",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                owner);
        ReflectionTestUtils.setField(meeting, "id", 99L);
        meeting.addParticipant(new MeetingParticipant(meeting, owner, InviteStatus.ACCEPTED));

        String ical = service.render(owner, List.of(meeting));

        assertThat(ical).contains("\r\n");
        assertThat(ical).contains("X-WR-CALNAME:alice\\,boss's meetings\r\n");
        assertThat(ical).contains("SUMMARY:Plan\\, review\\; ship \\\\ safely\r\n");
        assertThat(ical).contains("DESCRIPTION:Line one\\nLine two\\; with comma\\, and slash \\\\\r\n");
        assertThat(ical).contains("DTSTART:20260601T100000Z\r\n");
        assertThat(ical).contains("DTEND:20260601T110000Z\r\n");
    }

    @Test
    void renderMarksPendingMeetingsTentativeAndAcceptedMeetingsConfirmed() {
        User alice = new User("alice", "alice@example.com", "hash");
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting pending = meeting(1L, "Pending", alice);
        pending.addParticipant(new MeetingParticipant(pending, alice, InviteStatus.ACCEPTED));
        pending.addParticipant(new MeetingParticipant(pending, bob, InviteStatus.PENDING));
        Meeting confirmed = meeting(2L, "Confirmed", alice);
        confirmed.addParticipant(new MeetingParticipant(confirmed, alice, InviteStatus.ACCEPTED));

        String ical = service.render(alice, List.of(pending, confirmed));

        assertThat(ical).contains("SUMMARY:Pending\r\n");
        assertThat(ical).contains("ATTENDEE;CN=bob;PARTSTAT=NEEDS-ACTION:mailto:bob@example.com");
        assertThat(ical).contains("STATUS:TENTATIVE\r\n");
        assertThat(ical).contains("SUMMARY:Confirmed\r\n");
        assertThat(ical).contains("ATTENDEE;CN=alice;PARTSTAT=ACCEPTED:mailto:alice@example.com");
        assertThat(ical).contains("STATUS:CONFIRMED\r\n");
    }

    @Test
    void renderMapsDeclinedParticipantsToDeclinedPartstat() {
        User alice = new User("alice", "alice@example.com", "hash");
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting meeting = meeting(3L, "Declined", alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.DECLINED));

        String ical = service.render(alice, List.of(meeting));

        assertThat(ical).contains("ATTENDEE;CN=bob;PARTSTAT=DECLINED:mailto:bob@example.com");
        assertThat(ical).contains("STATUS:TENTATIVE\r\n");
    }

    @Test
    void renderFoldsLongContentLinesAtSeventyFiveCharacters() {
        User alice = new User("alice", "alice@example.com", "hash");
        Meeting meeting = meeting(
                4L,
                "This meeting title is deliberately long enough to exceed the RFC 5545 content line limit",
                alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));

        String ical = service.render(alice, List.of(meeting));

        assertThat(ical.split("\r\n"))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(75));
    }

    private static Meeting meeting(Long id, String title, User organizer) {
        Meeting meeting = new Meeting(
                title,
                "",
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                organizer);
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }
}
