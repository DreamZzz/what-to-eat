package com.quickstart.template.contexts.account.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class DemoLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String username;
    private final String displayName;
    private final String email;
    private final String phone;
    private final String bio;
    private final String region;

    public DemoLoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.test-login.enabled:true}") boolean enabled,
            @Value("${app.demo.test-login.username:demo_guest}") String username,
            @Value("${app.demo.test-login.display-name:Demo Guest}") String displayName,
            @Value("${app.demo.test-login.email:demo-guest@example.com}") String email,
            @Value("${app.demo.test-login.phone:}") String phone,
            @Value("${app.demo.test-login.bio:Test mode account for product demos}") String bio,
            @Value("${app.demo.test-login.region:Demo}") String region) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.bio = bio;
        this.region = region;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public User getOrCreateDemoUser() {
        return findExistingDemoUser()
                .map(this::refreshDemoUserProfile)
                .orElseGet(this::createDemoUser);
    }

    private Optional<User> findExistingDemoUser() {
        Optional<User> byUsername = userRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername;
        }

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(email);
    }

    private User refreshDemoUserProfile(User user) {
        boolean changed = false;

        if (displayName != null && !displayName.isBlank() && !displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            changed = true;
        }

        if (user.getFailedLoginAttempts() == null || user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            changed = true;
        }

        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl("");
            changed = true;
        }

        if ((user.getBio() == null || user.getBio().isBlank()) && bio != null && !bio.isBlank()) {
            user.setBio(bio);
            changed = true;
        }

        if ((user.getRegion() == null || user.getRegion().isBlank()) && region != null && !region.isBlank()) {
            user.setRegion(region);
            changed = true;
        }

        return changed ? userRepository.save(user) : user;
    }

    private User createDemoUser() {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
        user.setEmail(email);
        user.setPhone(phone == null || phone.isBlank() ? null : phone);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAvatarUrl("");
        user.setBio(bio == null ? "" : bio);
        user.setGender("");
        user.setRegion(region == null ? "" : region);
        user.setFailedLoginAttempts(0);
        return userRepository.save(user);
    }
}
