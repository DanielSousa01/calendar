package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingParticipantRepository participantRepository,
                          UserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    private static String buildDescription(DiscoveredEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.description() != null && !event.description().isBlank()) {
            sb.append(event.description()).append("\n\n");
        }
        if (event.venue() != null && !event.venue().isBlank()) {
            sb.append("Venue: ").append(event.venue()).append("\n");
        }
        sb.append("Source: ").append(event.source());
        if (event.url() != null) sb.append(" (").append(event.url()).append(")");
        return sb.toString();
    }

    @Transactional
    public Meeting propose(User organizer, String title, String description,
                           Instant start, Instant end, List<String> inviteeUsernames) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        rejectOverlapping(organizer, start, end);

        Meeting meeting = new Meeting(title, description, start, end, organizer);

        // Organizer auto-accepts; the slot is blocked on their calendar immediately.
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

        Set<String> seen = new HashSet<>();
        seen.add(organizer.getUsername());
        for (String username : inviteeUsernames) {
            String normalized = username == null ? "" : username.trim();
            if (normalized.isEmpty() || !seen.add(normalized)) continue;
            User invitee = userRepository.findByUsername(normalized)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown invitee: " + normalized));
            rejectOverlapping(invitee, start, end);
            meeting.addParticipant(new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));
        }

        return meetingRepository.save(meeting);
    }

    public List<Meeting> calendarFor(User user) {
        return meetingRepository.findCalendarMeetings(user);
    }

    public List<MeetingParticipant> pendingInvitesFor(User user) {
        return participantRepository.findByUserAndStatus(user, InviteStatus.PENDING);
    }

    @Transactional
    public void respond(Long meetingId, User user, InviteStatus status) {
        if (status != InviteStatus.ACCEPTED && status != InviteStatus.DECLINED) {
            throw new IllegalArgumentException("Response must be ACCEPTED or DECLINED");
        }
        MeetingParticipant participant = participantRepository
                .findByMeetingIdAndUserId(meetingId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No invite found for this user"));
        participant.setStatus(status);
    }

    /**
     * Add a discovered event to the user's calendar. The user is the sole participant and
     * auto-accepts, so {@link Meeting#isConfirmed()} is true immediately — no invite flow.
     * If the event has no end time we default to two hours, which matches most concerts/games.
     */
    @Transactional
    public Meeting copyFromDiscovered(User user, DiscoveredEvent event) {
        Instant end = event.end() != null ? event.end() : event.start().plus(Duration.ofHours(2));
        rejectOverlapping(user, event.start(), end);
        String description = buildDescription(event);
        Meeting meeting = new Meeting(event.title(), description, event.start(), end, user);
        meeting.addParticipant(new MeetingParticipant(meeting, user, InviteStatus.ACCEPTED));
        return meetingRepository.save(meeting);
    }

    private void rejectOverlapping(User user, Instant start, Instant end) {
        if (!meetingRepository.findOverlapping(user, start, end).isEmpty()) {
            throw new IllegalArgumentException("User has an overlapping meeting: " + user.getUsername());
        }
    }

    /**
     * Used by the iCal feed; declined invites are filtered out elsewhere.
     */
    public List<Meeting> calendarForIcalToken(String token) {
        User user = userRepository.findByIcalToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid iCal token"));
        return new ArrayList<>(calendarFor(user));
    }
}
