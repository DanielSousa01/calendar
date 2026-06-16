package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Controller
public class MeetingController {

    private final MeetingService meetingService;
    private final UserService userService;

    public MeetingController(MeetingService meetingService, UserService userService) {
        this.meetingService = meetingService;
        this.userService = userService;
    }

    private static InviteStatus responseStatus(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "accept" -> InviteStatus.ACCEPTED;
            case "decline" -> InviteStatus.DECLINED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown invite response action");
        };
    }

    @GetMapping("/meetings/new")
    public String proposeForm() {
        return "propose";
    }

    @PostMapping("/meetings/new")
    public String propose(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
                          @RequestParam String title,
                          @RequestParam(required = false) String description,
                          @RequestParam String start,
                          @RequestParam String end,
                          @RequestParam(required = false, defaultValue = "") String invitees,
                          Model model) {
        User organizer = userService.requireByUsername(principal.getUsername());
        try {
            // datetime-local inputs are wall-clock without a zone — interpret as server local.
            var zone = ZoneId.systemDefault();
            var startInstant = LocalDateTime.parse(start).atZone(zone).toInstant();
            var endInstant = LocalDateTime.parse(end).atZone(zone).toInstant();
            List<String> invitedUsernames = Arrays.stream(invitees.split("[,\\s]+"))
                    .filter(s -> !s.isBlank())
                    .toList();
            meetingService.propose(organizer, title, description, startInstant, endInstant, invitedUsernames);
            return "redirect:/calendar";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("start", start);
            model.addAttribute("end", end);
            model.addAttribute("invitees", invitees);
            return "propose";
        }
    }

    @PostMapping("/meetings/{id}/respond")
    public String respond(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
                          @PathVariable Long id,
                          @RequestParam String action) {
        User user = userService.requireByUsername(principal.getUsername());
        InviteStatus status = responseStatus(action);
        try {
            meetingService.respond(id, user, status);
        } catch (IllegalArgumentException ex) {
            if ("No invite found for this user".equals(ex.getMessage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
            }
            throw ex;
        }
        return "redirect:/calendar";
    }
}
