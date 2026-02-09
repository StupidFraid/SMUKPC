package com.aspia.inventory.controller;

import com.aspia.inventory.model.AppUser;
import com.aspia.inventory.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ProfileController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("currentPage", "profile");
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/";

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Неверный текущий пароль");
            return "redirect:/profile";
        }

        if (newPassword.length() < 4) {
            redirectAttributes.addFlashAttribute("error", "Новый пароль должен содержать минимум 4 символа");
            return "redirect:/profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Пароль успешно изменён");
        return "redirect:/profile";
    }
}
