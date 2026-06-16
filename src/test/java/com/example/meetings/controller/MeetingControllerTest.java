package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MeetingControllerTest {

    private final MeetingService meetingService = mock(MeetingService.class);
    private final UserService userService = mock(UserService.class);
    private final MeetingController controller = new MeetingController(meetingService, userService);

    private static org.springframework.security.core.userdetails.User principal(String username) {
        return new org.springframework.security.core.userdetails.User(
                username,
                "password",
                java.util.List.of());
    }

    @Test
    void respondDeclineUpdatesInviteAndRedirectsToCalendar() {
        User user = new User("bob", "bob@example.com", "hash");
        when(userService.requireByUsername("bob")).thenReturn(user);

        String view = controller.respond(principal("bob"), 7L, " decline ");

        assertThat(view).isEqualTo("redirect:/calendar");
        verify(meetingService).respond(7L, user, InviteStatus.DECLINED);
    }

    @Test
    void respondRejectsNullActionAsBadRequest() {
        User user = new User("bob", "bob@example.com", "hash");
        when(userService.requireByUsername("bob")).thenReturn(user);

        assertThatThrownBy(() -> controller.respond(principal("bob"), 7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(meetingService);
    }

    @Test
    void respondMapsMissingInviteToNotFound() {
        User user = new User("bob", "bob@example.com", "hash");
        when(userService.requireByUsername("bob")).thenReturn(user);
        doThrow(new IllegalArgumentException("No invite found for this user"))
                .when(meetingService).respond(7L, user, InviteStatus.ACCEPTED);

        assertThatThrownBy(() -> controller.respond(principal("bob"), 7L, "accept"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }
}
