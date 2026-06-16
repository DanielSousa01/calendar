package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {
    List<MeetingParticipant> findByUserAndStatus(User user, InviteStatus status);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);
}
