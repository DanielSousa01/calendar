package com.example.meetings.model;

public enum InviteStatus {
    /**
     * The user has been invited but not yet responded. The slot is tentatively blocked.
     */
    PENDING,
    /**
     * The user has accepted; once every participant accepts the meeting is confirmed.
     */
    ACCEPTED,
    /**
     * The user has declined; the slot is freed on their calendar.
     */
    DECLINED
}
