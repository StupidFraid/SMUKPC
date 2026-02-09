package com.aspia.inventory.controller;

import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.repository.ComponentChangeRepository;
import com.aspia.inventory.repository.HostRepository;
import com.aspia.inventory.service.AspiaSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final HostRepository hostRepository;
    private final ComponentChangeRepository changeRepository;
    private final AspiaSyncService syncService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public DashboardController(HostRepository hostRepository,
                               ComponentChangeRepository changeRepository,
                               AspiaSyncService syncService) {
        this.hostRepository = hostRepository;
        this.changeRepository = changeRepository;
        this.syncService = syncService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("currentPage", "dashboard");

        long totalHosts = hostRepository.count();
        long onlineHosts = hostRepository.countByOnlineTrue();
        long offlineHosts = hostRepository.countByOnlineFalse();
        long errorHosts = hostRepository.countBySyncErrorNotNull();
        long unacknowledgedChanges = changeRepository.countByAcknowledgedFalse();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalHosts", totalHosts);
        stats.put("onlineHosts", onlineHosts);
        stats.put("offlineHosts", offlineHosts);
        stats.put("errorHosts", errorHosts);
        stats.put("unacknowledgedChanges", unacknowledgedChanges);
        model.addAttribute("stats", stats);

        // Последние непросмотренные изменения
        List<ComponentChange> changes = changeRepository.findTop20ByAcknowledgedFalseOrderByDetectedAtDesc();
        List<Map<String, String>> recentChanges = new ArrayList<>();
        for (ComponentChange c : changes) {
            Map<String, String> change = new HashMap<>();
            change.put("id", String.valueOf(c.getId()));
            change.put("hostName", c.getHost().getDisplayName());
            change.put("hostId", String.valueOf(c.getHost().getId()));
            String groupNames = c.getHost().getGroups().stream()
                    .map(g -> g.getName())
                    .collect(Collectors.joining(", "));
            change.put("hostGroup", groupNames);
            change.put("componentType", c.getComponentType());
            change.put("changeType", c.getChangeType());
            change.put("oldValue", c.getOldValue() != null ? c.getOldValue() : "");
            change.put("newValue", c.getNewValue() != null ? c.getNewValue() : "");
            change.put("changeDate", c.getDetectedAt().format(DATE_FMT));
            recentChanges.add(change);
        }
        model.addAttribute("recentChanges", recentChanges);

        // Информация о синхронизации
        model.addAttribute("lastSyncTime", syncService.getLastSyncTime() != null
                ? syncService.getLastSyncTime().format(DATE_FMT) : "Ещё не выполнялась");
        model.addAttribute("lastSyncStatus", syncService.getLastSyncStatus());
        model.addAttribute("syncing", syncService.isSyncing());

        return "dashboard";
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Map<String, Object> dashboardApi() {
        Map<String, Object> result = new HashMap<>();

        long totalHosts = hostRepository.count();
        long onlineHosts = hostRepository.countByOnlineTrue();
        long offlineHosts = hostRepository.countByOnlineFalse();
        long errorHosts = hostRepository.countBySyncErrorNotNull();
        long unacknowledgedChanges = changeRepository.countByAcknowledgedFalse();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalHosts", totalHosts);
        stats.put("onlineHosts", onlineHosts);
        stats.put("offlineHosts", offlineHosts);
        stats.put("errorHosts", errorHosts);
        stats.put("unacknowledgedChanges", unacknowledgedChanges);
        result.put("stats", stats);

        List<ComponentChange> changes = changeRepository.findTop20ByAcknowledgedFalseOrderByDetectedAtDesc();
        List<Map<String, String>> recentChanges = new ArrayList<>();
        for (ComponentChange c : changes) {
            Map<String, String> change = new HashMap<>();
            change.put("id", String.valueOf(c.getId()));
            change.put("hostName", c.getHost().getDisplayName());
            change.put("hostId", String.valueOf(c.getHost().getId()));
            String groupNames = c.getHost().getGroups().stream()
                    .map(g -> g.getName())
                    .collect(Collectors.joining(", "));
            change.put("hostGroup", groupNames);
            change.put("componentType", c.getComponentType());
            change.put("changeType", c.getChangeType());
            change.put("oldValue", c.getOldValue() != null ? c.getOldValue() : "");
            change.put("newValue", c.getNewValue() != null ? c.getNewValue() : "");
            change.put("changeDate", c.getDetectedAt().format(DATE_FMT));
            recentChanges.add(change);
        }
        result.put("recentChanges", recentChanges);

        result.put("lastSyncTime", syncService.getLastSyncTime() != null
                ? syncService.getLastSyncTime().format(DATE_FMT) : "Ещё не выполнялась");
        result.put("lastSyncStatus", syncService.getLastSyncStatus());
        result.put("syncing", syncService.isSyncing());

        return result;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sync")
    public String sync(RedirectAttributes redirectAttributes) {
        int count = syncService.syncHostList();
        if (count > 0) {
            redirectAttributes.addFlashAttribute("syncMessage",
                    "Синхронизация завершена: " + count + " хостов обновлено");
        } else {
            redirectAttributes.addFlashAttribute("syncError",
                    syncService.getLastSyncStatus() != null ? syncService.getLastSyncStatus() : "Ошибка синхронизации");
        }
        return "redirect:/";
    }
}
