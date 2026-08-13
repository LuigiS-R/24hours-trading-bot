package com.ai_got_this.accounts_service.controller;

import com.ai_got_this.accounts_service.dto.ErrorResponse;
import com.ai_got_this.accounts_service.dto.WatchlistItemResponse;
import com.ai_got_this.accounts_service.service.WatchlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public ResponseEntity<?> getWatchlists(@RequestParam String userId) {
        try {
            List<WatchlistItemResponse> watchlists = watchlistService.getWatchlists(userId);
            return ResponseEntity.ok(watchlists);
        }
        catch (RuntimeException ex) {
            ErrorResponse errorResponse = new ErrorResponse(500, "Failed to handle watchlist request for userId " + userId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
