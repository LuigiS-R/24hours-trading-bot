package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import com.ai_got_this.account_service.service.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {
    final CurrentUserService currentUserService;
    final TradingAccountProjectionRepository tradingAccountProjectionRepository;
    final PositionProjectionRepository positionProjectionRepository;
    final OrderProjectionRepository orderProjectionRepository;

    public PortfolioController(
            CurrentUserService c,
            TradingAccountProjectionRepository a,
            PositionProjectionRepository p,
            OrderProjectionRepository o) {
        currentUserService = c;
        tradingAccountProjectionRepository = a;
        positionProjectionRepository = p;
        orderProjectionRepository = o;
    }

    record Portfolio(
            TradingAccountProjection account,
            List<PositionProjection> positions,
            Instant asOf,
            String freshness
    ) {}

    @GetMapping("/alpaca/connections/{id}/portfolio")
    public Portfolio getPortfolio(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        TradingAccountProjection a = tradingAccountProjectionRepository.findById(id).orElse(null);

        return new Portfolio(a, positionProjectionRepository.findByConnectionId(id), a == null ? null : a.getLastSyncedAt(), freshness(a));
    }

    @GetMapping("/alpaca/connections/{id}/positions")
    public List<PositionProjection> getPositions(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        return positionProjectionRepository.findByConnectionId(id);
    }

    @GetMapping("/alpaca/connections/{id}/orders")
    public List<OrderProjection> getOrders(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        return orderProjectionRepository.findTop20ByConnectionIdOrderByUpdatedAtDesc(id);
    }

    private String freshness(TradingAccountProjection accountProjection) {
        if (accountProjection == null || accountProjection.getLastSyncedAt() == null) {
            return "UNAVAILABLE";
        }

        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(10));
        if (accountProjection.getLastSyncedAt().isBefore(staleBefore)) {
            return "STALE";
        }

        return "FRESH";
    }
}