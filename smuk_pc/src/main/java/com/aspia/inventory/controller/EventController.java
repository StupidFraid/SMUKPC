package com.aspia.inventory.controller;

import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.repository.ComponentChangeRepository;
import com.aspia.inventory.service.TelegramNotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class EventController {

    private final ComponentChangeRepository changeRepository;
    private final TelegramNotificationService telegramService;

    public EventController(ComponentChangeRepository changeRepository,
                           TelegramNotificationService telegramService) {
        this.changeRepository = changeRepository;
        this.telegramService = telegramService;
    }

    @GetMapping("/events")
    public String events(@RequestParam(required = false) String filter,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                         Model model) {
        model.addAttribute("currentPage", "events");

        List<ComponentChange> allChanges;
        if (dateFrom != null || dateTo != null) {
            LocalDateTime from = dateFrom != null
                    ? dateFrom.atStartOfDay()
                    : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime to = dateTo != null
                    ? dateTo.atTime(LocalTime.MAX)
                    : LocalDateTime.now();
            allChanges = changeRepository.findByDetectedAtBetweenOrderByDetectedAtDesc(from, to);
        } else {
            allChanges = changeRepository.findTop50ByOrderByDetectedAtDesc();
        }

        long unacknowledgedCount = changeRepository.countByAcknowledgedFalse();

        model.addAttribute("events", allChanges);
        model.addAttribute("unacknowledgedCount", unacknowledgedCount);
        model.addAttribute("filter", filter != null ? filter : "all");
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);

        return "events";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/events/{id}/acknowledge")
    public String acknowledgeOne(@PathVariable Long id,
                                 @RequestParam(required = false) String redirectTo,
                                 Principal principal) {
        changeRepository.findById(id).ifPresent(change -> {
            change.setAcknowledged(true);
            change.setAcknowledgedAt(LocalDateTime.now());
            change.setAcknowledgedBy(principal.getName());
            changeRepository.save(change);
            telegramService.notifyChangesAcknowledged(
                    principal.getName(),
                    change.getHost().getDisplayName(),
                    java.util.Collections.singletonList(new TelegramNotificationService.ChangeInfo(
                            change.getHost().getDisplayName(), change.getComponentType(),
                            change.getChangeType(), change.getOldValue(), change.getNewValue())));
        });
        return "redirect:" + (redirectTo != null ? redirectTo : "/events");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/events/acknowledge-all")
    public String acknowledgeAll(@RequestParam(required = false) String redirectTo,
                                 Principal principal) {
        List<ComponentChange> unacknowledged = changeRepository.findAll().stream()
                .filter(c -> !c.isAcknowledged())
                .collect(java.util.stream.Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        String username = principal.getName();
        for (ComponentChange c : unacknowledged) {
            c.setAcknowledged(true);
            c.setAcknowledgedAt(now);
            c.setAcknowledgedBy(username);
        }
        changeRepository.saveAll(unacknowledged);
        if (!unacknowledged.isEmpty()) {
            List<TelegramNotificationService.ChangeInfo> infos = new java.util.ArrayList<>();
            for (ComponentChange c : unacknowledged) {
                infos.add(new TelegramNotificationService.ChangeInfo(
                        c.getHost().getDisplayName(), c.getComponentType(),
                        c.getChangeType(), c.getOldValue(), c.getNewValue()));
            }
            telegramService.notifyAllChangesAcknowledged(username, infos);
        }
        return "redirect:" + (redirectTo != null ? redirectTo : "/events");
    }
}
