package com.example.meetings.service;

import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a VCALENDAR document for a user. STATUS reflects whether every participant
 * has accepted: CONFIRMED vs TENTATIVE for proposed-but-not-fully-accepted meetings.
 */
@Service
public class ICalService {

    private static final DateTimeFormatter ICAL_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static String partStat(com.example.meetings.model.InviteStatus status) {
        return switch (status) {
            case ACCEPTED -> "ACCEPTED";
            case DECLINED -> "DECLINED";
            case PENDING -> "NEEDS-ACTION";
        };
    }

    private static void appendLine(StringBuilder sb, String line) {
        while (line.length() > 75) {
            sb.append(line, 0, 75).append("\r\n");
            line = " " + line.substring(75);
        }
        sb.append(line).append("\r\n");
    }

    /**
     * Escape per RFC 5545 §3.3.11.
     */
    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public String render(User owner, List<Meeting> meetings) {
        StringBuilder sb = new StringBuilder();
        // iCalendar uses CRLF line endings per RFC 5545.
        appendLine(sb, "BEGIN:VCALENDAR");
        appendLine(sb, "VERSION:2.0");
        appendLine(sb, "PRODID:-//meetings-app//EN");
        appendLine(sb, "CALSCALE:GREGORIAN");
        appendLine(sb, "METHOD:PUBLISH");
        appendLine(sb, "X-WR-CALNAME:" + escape(owner.getUsername() + "'s meetings"));

        String now = ICAL_FORMAT.format(java.time.Instant.now());
        for (Meeting m : meetings) {
            appendLine(sb, "BEGIN:VEVENT");
            appendLine(sb, "UID:meeting-" + m.getId() + "@meetings-app");
            appendLine(sb, "DTSTAMP:" + now);
            appendLine(sb, "DTSTART:" + ICAL_FORMAT.format(m.getStartTime()));
            appendLine(sb, "DTEND:" + ICAL_FORMAT.format(m.getEndTime()));
            appendLine(sb, "SUMMARY:" + escape(m.getTitle()));
            if (m.getDescription() != null && !m.getDescription().isBlank()) {
                appendLine(sb, "DESCRIPTION:" + escape(m.getDescription()));
            }
            appendLine(sb, "ORGANIZER;CN=" + escape(m.getOrganizer().getUsername())
                    + ":mailto:" + escape(m.getOrganizer().getEmail()));
            for (MeetingParticipant p : m.getParticipants()) {
                appendLine(sb, "ATTENDEE;CN=" + escape(p.getUser().getUsername())
                        + ";PARTSTAT=" + partStat(p.getStatus())
                        + ":mailto:" + escape(p.getUser().getEmail()));
            }
            appendLine(sb, "STATUS:" + (m.isConfirmed() ? "CONFIRMED" : "TENTATIVE"));
            appendLine(sb, "END:VEVENT");
        }

        appendLine(sb, "END:VCALENDAR");
        return sb.toString();
    }
}
