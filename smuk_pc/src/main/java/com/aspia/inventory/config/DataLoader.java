package com.aspia.inventory.config;

import com.aspia.inventory.model.AppUser;
import com.aspia.inventory.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode("admin"), "ADMIN", "Администратор"));
            userRepository.save(new AppUser("user", passwordEncoder.encode("user"), "USER", "Пользователь"));
            System.out.println("Созданы начальные пользователи: admin, user");
        }
    }
}
