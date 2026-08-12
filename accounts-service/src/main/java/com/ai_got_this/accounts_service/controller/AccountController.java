package com.ai_got_this.accounts_service.controller;

import com.ai_got_this.accounts_service.dto.ErrorResponse;
import com.ai_got_this.accounts_service.entity.Account;
import com.ai_got_this.accounts_service.entity.User;
import com.ai_got_this.accounts_service.exception.AccountNotFoundException;
import com.ai_got_this.accounts_service.exception.UserAlreadyExistsException;
import com.ai_got_this.accounts_service.service.AccountService;
import com.ai_got_this.accounts_service.service.UserService;
import com.ai_got_this.accounts_service.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts/api")
public class AccountController {
    private final AccountService accountService;
    private final UserService userService;
    private final WatchlistService watchlistService;

    public AccountController(AccountService accountService, UserService userService, WatchlistService watchlistService) {
        this.accountService = accountService;
        this.userService = userService;
        this.watchlistService = watchlistService;
    }

    @PostMapping("/user")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        }
        catch (UserAlreadyExistsException ex) {
            ErrorResponse errorResponse = new ErrorResponse(409, ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
    }

    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@RequestParam String brokerName, @RequestParam String authToken, @RequestParam String userId) {
        try {
            Account account = accountService.getAccount(authToken, userId, brokerName);
            return ResponseEntity.ok(account);
        }
        catch (AccountNotFoundException ex) {
            ErrorResponse errorResponse = new ErrorResponse(404, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        catch (RuntimeException ex) {
            ErrorResponse errorResponse = new ErrorResponse(500, "Failed to get account");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/watchlist")
    public ResponseEntity<?> getWatchlistUsersByTicker(@RequestParam String ticker) {
        try {
            List<UUID> users = watchlistService.getUsers(ticker);
            return ResponseEntity.ok(users);
        }
        catch (RuntimeException ex) {
            ErrorResponse errorResponse = new ErrorResponse(500, "Failed to get watchlist users");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}