package com.ai_got_this.accounts_service.dto;

import java.util.UUID;

public record WatchlistItemResponse(
        UUID watchlistId,
        String watchlistName,
        String ticker,
        boolean isActive
) {
}
