package com.quickstart.template.contexts.account.api;

import com.quickstart.template.contexts.account.api.dto.UserDTO;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.application.UserService;
import com.quickstart.template.platform.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private final UserService userService;
    private final CurrentUserService currentUserService;
    
    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<User> user = userService.findById(id);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        return ResponseEntity.ok(UserDTO.fromUser(user.get()));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateUserProfile(
            @PathVariable Long id,
            @Valid @RequestBody UserDTO userDTO) {
        Optional<User> currentUser = currentUserService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        if (!currentUser.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot update another user's profile");
        }
        
        User updatedUser = new User();
        updatedUser.setDisplayName(userDTO.getDisplayName());
        updatedUser.setAvatarUrl(userDTO.getAvatarUrl());
        updatedUser.setBio(userDTO.getBio());
        updatedUser.setGender(userDTO.getGender());
        updatedUser.setBirthday(userDTO.getBirthday());
        updatedUser.setRegion(userDTO.getRegion());
        updatedUser.setPhone(userDTO.getPhone());
        if (userDTO.getEmail() != null && !userDTO.getEmail().isEmpty()) {
            updatedUser.setEmail(userDTO.getEmail());
        }
        
        Optional<User> savedUser = userService.updateProfile(id, updatedUser);
        if (savedUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already in use or invalid update");
        }
        
        return ResponseEntity.ok(UserDTO.fromUser(savedUser.get()));
    }
}
