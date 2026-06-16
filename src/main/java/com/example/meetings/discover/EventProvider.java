package com.example.meetings.discover;

import java.util.List;

public interface EventProvider {
    /**
     * Human-readable name shown in the UI ("Ticketmaster", "SeatGeek").
     */
    String name();

    /**
     * True when this provider has been configured (e.g. has an API key).
     */
    boolean isConfigured();

    /**
     * Best-effort search. Implementations should swallow network/parse errors and return an
     * empty list — discovery is a "nice to have" and one provider's outage shouldn't 500 the page.
     */
    List<DiscoveredEvent> search(String query);
}
