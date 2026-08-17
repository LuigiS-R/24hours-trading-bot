package com.ai_got_this.order_execution_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlpacaWebSocketReconnectSupervisorTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectProvider<WebSocketConnectionManager> provider = mock(ObjectProvider.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final WebSocketConnectionManager manager = mock(WebSocketConnectionManager.class);
    private final ScheduledFuture<?> firstTask = mock(ScheduledFuture.class);
    private AlpacaWebSocketReconnectSupervisor supervisor;

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(manager);
        when(manager.isConnected()).thenReturn(false);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> firstTask);
        supervisor = new AlpacaWebSocketReconnectSupervisor(provider, scheduler, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void schedulesOnlyOneInitialReconnectWhenRepeatedDisconnectsArrive() {
        supervisor.onConnectionClosed(CloseStatus.GOING_AWAY);
        supervisor.onConnectionClosed(CloseStatus.GOING_AWAY);

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(1)).schedule(any(Runnable.class), when.capture());
        assertEquals(NOW.plusSeconds(5), when.getValue());
    }

    @Test
    void startsTheConnectionManagerAndSchedulesATimeoutForAnUnconfirmedReconnect() {
        supervisor.onConnectionClosed(CloseStatus.GOING_AWAY);
        Runnable reconnect = scheduledRunnables().getValue();

        reconnect.run();

        verify(manager).stop();
        verify(manager).start();
        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(2)).schedule(any(Runnable.class), when.capture());
        assertEquals(NOW.plusSeconds(5), when.getAllValues().get(0));
        assertEquals(NOW.plusSeconds(15), when.getAllValues().get(1));
    }

    @Test
    void backsOffAfterAReconnectStartFailure() {
        doThrow(new IllegalStateException("start failed")).when(manager).stop();
        supervisor.onConnectionClosed(CloseStatus.GOING_AWAY);

        scheduledRunnables().getValue().run();

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(2)).schedule(any(Runnable.class), when.capture());
        assertEquals(NOW.plusSeconds(10), when.getAllValues().get(1));
    }

    @Test
    void shutdownCancelsThePendingTaskAndPreventsFurtherReconnects() {
        supervisor.onConnectionClosed(CloseStatus.GOING_AWAY);

        supervisor.stopReconnects();
        supervisor.destroy();
        supervisor.onTransportError(new RuntimeException("late error"));

        verify(firstTask, times(1)).cancel(false);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void doesNothingWhenTheConnectionManagerIsUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);

        supervisor.ensureInitialConnection();

        verifyNoInteractions(scheduler);
    }

    private ArgumentCaptor<Runnable> scheduledRunnables() {
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(runnable.capture(), any(Instant.class));
        return runnable;
    }
}
