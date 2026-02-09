package com.aspia.inventory.controller;

import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.HostSoftware;
import com.aspia.inventory.model.SoftwareExclusion;
import com.aspia.inventory.repository.ComponentChangeRepository;
import com.aspia.inventory.repository.HostGroupRepository;
import com.aspia.inventory.repository.HostRepository;
import com.aspia.inventory.repository.HostSoftwareRepository;
import com.aspia.inventory.repository.SoftwareExclusionRepository;
import com.aspia.inventory.service.InventoryExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final HostRepository hostRepository;
    private final HostSoftwareRepository softwareRepository;
    private final HostGroupRepository groupRepository;
    private final ComponentChangeRepository changeRepository;
    private final SoftwareExclusionRepository exclusionRepository;
    private final InventoryExportService exportService;

    public InventoryController(HostRepository hostRepository,
                               HostSoftwareRepository softwareRepository,
                               HostGroupRepository groupRepository,
                               ComponentChangeRepository changeRepository,
                               SoftwareExclusionRepository exclusionRepository,
                               InventoryExportService exportService) {
        this.hostRepository = hostRepository;
        this.softwareRepository = softwareRepository;
        this.groupRepository = groupRepository;
        this.changeRepository = changeRepository;
        this.exclusionRepository = exclusionRepository;
        this.exportService = exportService;
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("currentPage", "inventory");

        List<Host> hosts = hostRepository.findAll();
        model.addAttribute("hosts", hosts);

        Set<Long> changedHostIds = new HashSet<>(
                changeRepository.findDistinctHostIdsWithUnacknowledgedChanges());
        model.addAttribute("changedHostIds", changedHostIds);

        model.addAttribute("groups", groupRepository.findAll());

        List<Object[]> softwareList = softwareRepository.findSoftwareSummary();
        model.addAttribute("softwareList", softwareList);

        Set<String> excludedSoftwareNames = exclusionRepository.findByHostIsNull().stream()
                .map(SoftwareExclusion::getSoftwareName).collect(Collectors.toSet());
        model.addAttribute("excludedSoftwareNames", excludedSoftwareNames);

        return "inventory";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/inventory/software/toggle-tracking")
    @ResponseBody
    @Transactional
    public Map<String, Object> toggleGlobalTracking(@RequestParam String name) {
        Map<String, Object> result = new HashMap<>();
        Optional<SoftwareExclusion> existing = exclusionRepository.findBySoftwareNameAndHostIsNull(name);
        if (existing.isPresent()) {
            exclusionRepository.delete(existing.get());
            result.put("tracked", true);
        } else {
            exclusionRepository.save(new SoftwareExclusion(name, null));
            result.put("tracked", false);
        }
        result.put("success", true);
        return result;
    }

    @GetMapping("/inventory/software")
    public String softwareDetail(@RequestParam String name, Model model) {
        model.addAttribute("currentPage", "inventory");
        model.addAttribute("softwareName", name);

        List<HostSoftware> entries = softwareRepository.findByNameWithHost(name);
        model.addAttribute("softwareEntries", entries);

        String publisher = entries.isEmpty() ? "—" :
                (entries.get(0).getPublisher() != null ? entries.get(0).getPublisher() : "—");
        model.addAttribute("publisher", publisher);

        return "software-detail";
    }

    // ==================== Экспорт ====================

    @GetMapping("/inventory/export/hardware/excel")
    public ResponseEntity<byte[]> exportHardwareExcel() {
        try {
            List<Host> hosts = hostRepository.findAll();
            byte[] data = exportService.exportHardwareExcel(hosts);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hardware_" + timestamp() + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта оборудования в Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/inventory/export/hardware/pdf")
    public ResponseEntity<byte[]> exportHardwarePdf() {
        try {
            List<Host> hosts = hostRepository.findAll();
            byte[] data = exportService.exportHardwarePdf(hosts);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hardware_" + timestamp() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта оборудования в PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/inventory/export/software/excel")
    public ResponseEntity<byte[]> exportSoftwareExcel() {
        try {
            List<Object[]> softwareList = softwareRepository.findSoftwareSummary();
            byte[] data = exportService.exportSoftwareExcel(softwareList);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=software_" + timestamp() + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта ПО в Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/inventory/export/software/pdf")
    public ResponseEntity<byte[]> exportSoftwarePdf() {
        try {
            List<Object[]> softwareList = softwareRepository.findSoftwareSummary();
            byte[] data = exportService.exportSoftwarePdf(softwareList);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=software_" + timestamp() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта ПО в PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
    }
}
