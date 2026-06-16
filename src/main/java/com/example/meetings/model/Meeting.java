package com.example.meetings.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private User organizer;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final Set<MeetingParticipant> participants = new HashSet<>();

    protected Meeting() {
    }

    public Meeting(String title, String description, Instant startTime, Instant endTime, User organizer) {
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.organizer = organizer;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public User getOrganizer() {
        return organizer;
    }

    public Set<MeetingParticipant> getParticipants() {
        return participants;
    }

    public void addParticipant(MeetingParticipant participant) {
        this.participants.add(participant);
    }

    /**
     * A meeting is confirmed when every invited participant (including the organizer) has accepted.
     */
    public boolean isConfirmed() {
        if (participants.isEmpty()) return false;
        return participants.stream().allMatch(p -> p.getStatus() == InviteStatus.ACCEPTED);
    }
}
