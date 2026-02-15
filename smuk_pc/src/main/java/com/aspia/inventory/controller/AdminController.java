package com.aspia.inventory.controller;

import com.aspia.inventory.model.AppUser;
import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.HostGroup;
import com.aspia.inventory.repository.AppUserRepository;
import com.aspia.inventory.repository.HostGroupRepository;
import com.aspia.inventory.repository.HostRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
public class AdminController {

    private final AppUserRepository userRepository;
    private final HostGroupRepository groupRepository;
    private final HostRepository hostRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(AppUserRepository userRepository,
                           HostGroupRepository groupRepository,
                           HostRepository hostRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.hostRepository = hostRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("currentPage", "admin");
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("groups", groupRepository.findAll());
        return "admin";
    }

    // ==================== Пользователи ====================

    @PostMapping("/admin/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam String password,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        if (userRepository.findByUsername(username).isPresent()) {
            redirectAttributes.addFlashAttribute("userError", "Пользователь с логином «" + username + "» уже существует");
            return "redirect:/admin";
        }
        AppUser user = new AppUser(username, passwordEncoder.encode(password), role, displayName);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("userSuccess", "Пользователь «" + username + "» создан");
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/password")
    public String changePassword(@PathVariable Long id,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) return "redirect:/admin";
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("userSuccess", "Пароль пользователя «" + user.getUsername() + "» изменён");
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) return "redirect:/admin";
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("userSuccess",
                "Пользователь «" + user.getUsername() + "» " + (user.isEnabled() ? "активирован" : "деактивирован"));
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) return "redirect:/admin";
        if (user.getUsername().equals(principal.getName())) {
            redirectAttributes.addFlashAttribute("userError", "Нельзя удалить самого себя");
            return "redirect:/admin";
        }
        userRepository.delete(user);
        redirectAttributes.addFlashAttribute("userSuccess", "Пользователь «" + user.getUsername() + "» удалён");
        return "redirect:/admin";
    }

    // ==================== Группы ====================

    @PostMapping("/admin/groups")
    public String createGroup(@RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) List<String> trackedComponents) {
        HostGroup group = new HostGroup();
        group.setName(name);
        group.setDescription(description);
        if (trackedComponents != null && !trackedComponents.isEmpty()) {
            group.setTrackedComponents(String.join(",", trackedComponents));
        }
        groupRepository.save(group);
        return "redirect:/admin";
    }

    @PostMapping("/admin/groups/{id}/tracking")
    public String updateGroupTracking(@PathVariable Long id,
                                      @RequestParam(required = false) List<String> trackedComponents) {
        HostGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return "redirect:/admin";
        if (trackedComponents != null && !trackedComponents.isEmpty()) {
            group.setTrackedComponents(String.join(",", trackedComponents));
        } else {
            group.setTrackedComponents(null);
        }
        groupRepository.save(group);
        return "redirect:/admin";
    }

    @PostMapping("/admin/groups/{id}/delete")
    @org.springframework.transaction.annotation.Transactional
    public String deleteGroup(@PathVariable Long id) {
        HostGroup group = groupRepository.findById(id).orElse(null);
        if (group != null) {
            for (Host host : hostRepository.findAll()) {
                if (host.getGroups().remove(group)) {
                    hostRepository.save(host);
                }
            }
            groupRepository.delete(group);
        }
        return "redirect:/admin";
    }
}
