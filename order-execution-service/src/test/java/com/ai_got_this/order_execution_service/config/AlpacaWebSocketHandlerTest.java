package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.service.PublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AlpacaWebSocketHandlerTest {
    private PublisherService publisher;
    private AlpacaWebSocketReconnectSupervisor supervisor;
    private WebSocketSession session;
    private TestableHandler handler;

    @BeforeEach
    void setUp() {
        publisher = mock(PublisherService.class);
        supervisor = mock(AlpacaWebSocketReconnectSupervisor.class);
        session = mock(WebSocketSession.class);
        AlpacaProperties properties = new AlpacaProperties();
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        handler = new TestableHandler(new ObjectMapper(), publisher, properties, supervisor);
    }

    @Test
    void subscribesAndResetsTheReconnectSupervisorWhenConnected() throws Exception {
        handler.afterConnectionEstablished(session);

        verify(supervisor).onConnectionEstablished();
        verify(session).sendMessage(eq(new TextMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}")));
    }

    @Test
    void propagatesSubscriptionSendFailure() throws Exception {
        doThrow(new java.io.IOException("socket closed")).when(session).sendMessage(any(TextMessage.class));

        assertThrows(java.io.IOException.class, () -> handler.afterConnectionEstablished(session));
    }

    @Test
    void forwardsEveryTradeUpdateInABinaryArray() {
        handler.receive("[{\"stream\":\"trade_updates\",\"data\":{\"event\":\"partial_fill\",\"order\":{\"clientOrderId\":\"one\"}}},{\"stream\":\"trade_updates\",\"data\":{\"event\":\"fill\",\"order\":{\"clientOrderId\":\"two\"}}}]");

        verify(publisher, times(2)).publishOrderEvent(any(OrderEvent.class));
    }

    @Test
    void continuesProcessingLaterUpdatesWhenPublishingAnEarlierUpdateFails() {
        doThrow(new IllegalStateException("publisher unavailable")).doNothing()
                .when(publisher).publishOrderEvent(any(OrderEvent.class));

        handler.receive("[{\"stream\":\"trade_updates\",\"data\":{\"event\":\"new\",\"order\":{\"clientOrderId\":\"one\"}}},{\"stream\":\"trade_updates\",\"data\":{\"event\":\"fill\",\"order\":{\"clientOrderId\":\"two\"}}}]");

        verify(publisher, times(2)).publishOrderEvent(any(OrderEvent.class));
    }

    @Test
    void doesNotPublishProtocolAcknowledgementsErrorsUnknownOrMalformedMessages() {
        handler.receive("[{\"stream\":\"authorization\",\"data\":{\"status\":\"authorized\"}},{\"stream\":\"listening\",\"data\":{\"streams\":[\"trade_updates\"]}},{\"action\":\"error\",\"data\":{\"error_message\":\"denied\"}},{\"stream\":\"other\"}]");
        handler.receive("not-json");

        verifyNoInteractions(publisher);
    }

    @Test
    void delegatesCloseAndTransportFailuresToTheReconnectSupervisor() {
        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);
        RuntimeException failure = new RuntimeException("connection lost");
        handler.handleTransportError(session, failure);

        verify(supervisor).onConnectionClosed(CloseStatus.GOING_AWAY);
        verify(supervisor).onTransportError(failure);
    }

    private static final class TestableHandler extends AlpacaWebSocketHandler {
        private TestableHandler(ObjectMapper objectMapper, PublisherService publisher, AlpacaProperties properties,
                                AlpacaWebSocketReconnectSupervisor supervisor) {
            super(objectMapper, publisher, properties, supervisor);
        }

        private void receive(String payload) {
            handleBinaryMessage(mock(WebSocketSession.class), new BinaryMessage(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
