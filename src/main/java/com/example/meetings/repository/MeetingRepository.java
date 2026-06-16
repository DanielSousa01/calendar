package com.example.meetings.repository;

import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    /**
     * Meetings on which a user appears either as organizer or as a non-declined participant.
     * Declined invites are filtered out so the slot frees up on the user's calendar.
     */
    @Query("""
            select distinct m from Meeting m
            left join m.participants p
            where (m.organizer = :user
                   or (p.user = :user and p.status <> com.example.meetings.model.InviteStatus.DECLINED))
            order by m.startTime
            """)
    List<Meeting> findCalendarMeetings(@Param("user") User user);

    /**
     * Used for conflict detection — same filter as above, restricted to a time window.
     */
    @Query("""
            select distinct m from Meeting m
            left join m.participants p
            where (m.organizer = :user
                   or (p.user = :user and p.status <> com.example.meetings.model.InviteStatus.DECLINED))
              and m.startTime < :end
              and m.endTime > :start
            """)
    List<Meeting> findOverlapping(@Param("user") User user,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);
}
