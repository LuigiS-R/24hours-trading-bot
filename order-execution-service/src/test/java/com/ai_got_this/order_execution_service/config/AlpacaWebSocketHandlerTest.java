package com.ai_got_this.order_execution_service.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.service.PublisherService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class AlpacaWebSocketHandlerTest {
    private static final UUID CONNECTION_ID = UUID.fromString("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64");

    private PublisherService publisher;
    private Runnable closedCallback;
    private WebSocketSession session;
    private TestableHandler handler;

    @BeforeEach
    void setUp() {
        publisher = mock(PublisherService.class);
        closedCallback = mock(Runnable.class);
        session = mock(WebSocketSession.class);
        AlpacaProperties properties = new AlpacaProperties();
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        handler = new TestableHandler(new ObjectMapper(), publisher, properties, closedCallback);
    }

    @Test
    void authenticatesWithTheConnectionOAuthTokenThenSubscribesAfterAuthorization() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.receive(session, "{\"stream\":\"authorization\",\"data\":{\"status\":\"authorized\"}}");

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messages.capture());
        tools.jackson.databind.JsonNode authentication = new ObjectMapper()
                .readTree(messages.getAllValues().get(0).getPayload());
        assertEquals("authenticate", authentication.path("action").asText());
        assertEquals("oauth-token", authentication.path("data").path("oauth_token").asText());
        assertEquals(
                "{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}",
                messages.getAllValues().get(1).getPayload());
    }

    @Test
    void attachesTheKnownConnectionIdToEveryTradeUpdateInABinaryArray() {
        handler.receive(session, "[{\"stream\":\"trade_updates\",\"data\":{\"event\":\"partial_fill\",\"order\":{\"clientOrderId\":\"one\"}}},{\"stream\":\"trade_updates\",\"data\":{\"event\":\"fill\",\"order\":{\"clientOrderId\":\"two\"}}}]");

        ArgumentCaptor<OrderEvent> events = ArgumentCaptor.forClass(OrderEvent.class);
        verify(publisher, times(2)).publishOrderEvent(events.capture());
        org.junit.jupiter.api.Assertions.assertTrue(events.getAllValues().stream()
                .allMatch(event -> CONNECTION_ID.toString().equals(event.getConnectionId())));
    }

    @Test
    void propagatesAuthenticationSendFailure() throws Exception {
        doThrow(new java.io.IOException("socket closed")).when(session).sendMessage(any(TextMessage.class));

        assertThrows(java.io.IOException.class, () -> handler.afterConnectionEstablished(session));
    }

    @Test
    void doesNotPublishProtocolAcknowledgementsErrorsUnknownOrMalformedMessages() {
        handler.receive(session, "[{\"stream\":\"authorization\",\"data\":{\"status\":\"unauthorized\"}},{\"stream\":\"listening\",\"data\":{\"streams\":[\"trade_updates\"]}},{\"action\":\"error\",\"data\":{\"error_message\":\"denied\"}},{\"stream\":\"other\"}]");
        handler.receive(session, "not-json");

        verifyNoInteractions(publisher);
    }

    @Test
    void removesItsRegistryEntryWhenTheSocketCloses() {
        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(closedCallback).run();
    }

    @Test
    void treatsTransportErrorsAsClosedSocketsSoTheRegistryCanReconnect() throws Exception {
        handler.handleTransportError(session, new IllegalStateException("network failure"));

        verify(session).close();
        verify(closedCallback).run();
    }

    private static final class TestableHandler extends AlpacaWebSocketHandler {
        private TestableHandler(
                ObjectMapper objectMapper,
                PublisherService publisher,
                AlpacaProperties properties,
                Runnable closedCallback) {
            super(CONNECTION_ID, "oauth-token", objectMapper, publisher, properties, closedCallback);
        }

        private void receive(WebSocketSession session, String payload) {
            handleBinaryMessage(session, new BinaryMessage(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
