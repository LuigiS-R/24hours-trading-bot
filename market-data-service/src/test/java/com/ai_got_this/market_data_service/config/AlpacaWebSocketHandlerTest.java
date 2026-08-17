package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.dto.MarketData;
import com.ai_got_this.market_data_service.service.MarketDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AlpacaWebSocketHandlerTest {
    private MarketDataPublisher publisher;
    private AlpacaWebSocketReconnectSupervisor reconnectSupervisor;
    private AlpacaWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        publisher = mock(MarketDataPublisher.class);
        reconnectSupervisor = mock(AlpacaWebSocketReconnectSupervisor.class);
        AlpacaProperties properties = new AlpacaProperties();
        properties.setSubscriptionMessage("{\"action\":\"subscribe\",\"bars\":[\"AAPL\"]}");
        handler = new AlpacaWebSocketHandler(new ObjectMapper(), publisher, properties, reconnectSupervisor);
    }

    @Test
    void establishedConnectionMarksSupervisorConnectedAndSendsConfiguredSubscription() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.afterConnectionEstablished(session);

        verify(reconnectSupervisor).onConnectionEstablished();
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertEquals("{\"action\":\"subscribe\",\"bars\":[\"AAPL\"]}", message.getValue().getPayload());
    }

    @Test
    void subscriptionSendFailureIsPropagatedToConnectionManager() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        doThrow(new IOException("socket closed")).when(session).sendMessage(any(TextMessage.class));

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> handler.afterConnectionEstablished(session));
        verify(reconnectSupervisor).onConnectionEstablished();
    }

    @Test
    void closeAndTransportErrorAreForwardedToReconnectSupervisor() {
        WebSocketSession session = mock(WebSocketSession.class);
        RuntimeException failure = new RuntimeException("network lost");

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);
        handler.handleTransportError(session, failure);

        verify(reconnectSupervisor).onConnectionClosed(CloseStatus.GOING_AWAY);
        verify(reconnectSupervisor).onTransportError(failure);
    }

    @Test
    void validBarIsNormalizedAndPublishedOnceToConfiguredBrokerRoute() {
        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":100.25,\"h\":105.5,\"l\":99.75,\"c\":104.0,\"v\":123456,\"n\":17,\"t\":\"2026-08-17T00:00:00Z\"}]");

        ArgumentCaptor<MarketData> marketData = ArgumentCaptor.forClass(MarketData.class);
        verify(publisher).publishMarketData(eq("market-data-exchange"), marketData.capture(), eq("market-data"));
        assertEquals(MarketData.builder().ticket("AAPL").open(100.25).high(105.5).low(99.75)
                .close(104.0).volume(123456L).build(), marketData.getValue());
    }

    @Test
    void unknownAndProviderErrorMessagesAreNeverPublished() {
        deliver("[{\"T\":\"error\",\"code\":401,\"msg\":\"rejected\"}]");
        deliver("[{\"T\":\"success\",\"msg\":\"authenticated\"}]");
        deliver("[{\"T\":\"subscription\",\"bars\":[\"AAPL\"],\"quotes\":[\"AAPL\"]}]");

        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-json", "{}", "[]", "[null]", "{\"T\":\"b\"}"})
    void malformedOrUnexpectedPayloadsAreContainedAndNeverPublished(String payload) {
        assertDoesNotThrow(() -> handler.handleMessage(mock(WebSocketSession.class), new TextMessage(payload)));

        verifyNoInteractions(publisher);
    }

    @Test
    void publisherFailureIsContainedSoOneBadEventDoesNotEscapeWebSocketCallback() {
        doThrow(new IllegalStateException("broker unavailable")).when(publisher)
                .publishMarketData(eq("market-data-exchange"), any(MarketData.class), eq("market-data"));

        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1}]");
        verify(publisher).publishMarketData(eq("market-data-exchange"), any(MarketData.class), eq("market-data"));
    }

    @Test
    void failurePublishingOneBarDoesNotPreventLaterBarsInTheSameFrame() {
        doThrow(new IllegalStateException("broker unavailable")).when(publisher)
                .publishMarketData(eq("market-data-exchange"), org.mockito.ArgumentMatchers.argThat(data -> "AAPL".equals(data.getTicket())), eq("market-data"));

        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1},{\"T\":\"b\",\"S\":\"MSFT\",\"o\":3,\"h\":4,\"l\":3,\"c\":4,\"v\":2}]");

        verify(publisher, org.mockito.Mockito.times(2)).publishMarketData(eq("market-data-exchange"), any(MarketData.class), eq("market-data"));
    }

    @Test
    void everyBarInProviderBatchIsProcessedInOrder() {
        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1},{\"T\":\"b\",\"S\":\"MSFT\",\"o\":3,\"h\":4,\"l\":3,\"c\":4,\"v\":2}]");

        ArgumentCaptor<MarketData> marketData = ArgumentCaptor.forClass(MarketData.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishMarketData(eq("market-data-exchange"), marketData.capture(), eq("market-data"));
        assertEquals(java.util.List.of("AAPL", "MSFT"), marketData.getAllValues().stream().map(MarketData::getTicket).toList());
    }

    @Test
    void invalidBarValuesAreForwardedWithoutValidationByCurrentImplementation() {
        deliver("[{\"T\":\"b\",\"S\":\"  aapl  \",\"o\":-1,\"h\":0,\"l\":10,\"c\":-2,\"v\":-1}]");

        ArgumentCaptor<MarketData> marketData = ArgumentCaptor.forClass(MarketData.class);
        verify(publisher).publishMarketData(eq("market-data-exchange"), marketData.capture(), eq("market-data"));
        assertEquals("  aapl  ", marketData.getValue().getTicket());
        assertEquals(-1.0, marketData.getValue().getOpen());
        assertEquals(-1L, marketData.getValue().getVolume());
    }

    private void deliver(String payload) {
        assertDoesNotThrow(() -> handler.handleMessage(mock(WebSocketSession.class), new TextMessage(payload)));
    }
}
