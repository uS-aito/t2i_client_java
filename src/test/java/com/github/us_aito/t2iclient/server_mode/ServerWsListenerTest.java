package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerWsListenerTest {

    private static final String PROMPT_ID = "pid-001";

    private WebSocket mockWs() {
        return mock(WebSocket.class);
    }

    private ServerWsListener newListener(
            String trackingPromptId,
            CompletableFuture<Void> future,
            Consumer<Iterable<com.fasterxml.jackson.databind.JsonNode>> imageConsumer) {
        return new ServerWsListener(trackingPromptId, future, imageConsumer);
    }

    // --- execution_success ---

    @Test
    void onText_executionSuccess_completesJobFuture() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        String msg = String.format(
            "{\"type\":\"execution_success\",\"data\":{\"prompt_id\":\"%s\"}}", PROMPT_ID);
        listener.onText(mockWs(), msg, true);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
    }

    // --- execution_error ---

    @Test
    void onText_executionError_completesExceptionally() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        String msg = String.format(
            "{\"type\":\"execution_error\",\"data\":{\"prompt_id\":\"%s\"}}", PROMPT_ID);
        listener.onText(mockWs(), msg, true);

        assertTrue(future.isCompletedExceptionally());
    }

    // --- executed: imageConsumer が呼ばれる ---

    @Test
    void onText_executed_callsImageConsumer() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<Iterable<com.fasterxml.jackson.databind.JsonNode>> captured = new AtomicReference<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, captured::set);

        String msg = String.format(
            "{\"type\":\"executed\",\"data\":{\"prompt_id\":\"%s\",\"output\":{\"images\":[{\"filename\":\"img.png\",\"type\":\"output\",\"subfolder\":\"\"}]}}}", PROMPT_ID);
        listener.onText(mockWs(), msg, true);

        assertNotNull(captured.get());
    }

    // --- prompt_id 不一致イベントは破棄 ---

    @Test
    void onText_wrongPromptId_futureRemainsIncomplete() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        String msg = "{\"type\":\"execution_success\",\"data\":{\"prompt_id\":\"other-pid\"}}";
        listener.onText(mockWs(), msg, true);

        assertFalse(future.isDone());
    }

    // --- 複数フレーム分割メッセージのバッファリング ---

    @Test
    void onText_multipleFrames_buffersUntilLast() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        String part1 = String.format("{\"type\":\"execution_success\",\"data\":{\"prompt_id\":\"%s\"", PROMPT_ID);
        String part2 = "}}";

        listener.onText(mockWs(), part1, false);
        assertFalse(future.isDone());

        listener.onText(mockWs(), part2, true);
        assertTrue(future.isDone());
    }

    // --- onClose: 未完了 future を例外完了 ---

    @Test
    void onClose_pendingFuture_completesExceptionally() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        listener.onClose(mockWs(), 1001, "going away");

        assertTrue(future.isCompletedExceptionally());
    }

    // --- onError: 未完了 future を例外完了 ---

    @Test
    void onError_pendingFuture_completesExceptionally() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerWsListener listener = newListener(PROMPT_ID, future, images -> {});

        listener.onError(mockWs(), new RuntimeException("network error"));

        assertTrue(future.isCompletedExceptionally());
    }
}
