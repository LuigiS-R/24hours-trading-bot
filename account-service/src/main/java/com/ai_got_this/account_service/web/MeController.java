package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    final CurrentUserService currentUserService;

    MeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public record Me(UUID userId, String email, String displayName, User.UserStatus status) {}

    public record Patch(String displayName) {}

    private Me dto(User u) {
        return new Me(u.getUserId(), u.getEmail(), u.getDisplayName(), u.getStatus());
    }

    @GetMapping
    public Me getCurrentUser() {
        User currentUser = currentUserService.getOrProvisionCurrentUser();

        return dto(currentUser);
    }

    @PatchMapping
    public Me updateProfile(@RequestBody Patch patch) {
        String displayName = patch.displayName();
        User updatedUser = currentUserService.updateDisplayName(displayName);

        return dto(updatedUser);
    }
}