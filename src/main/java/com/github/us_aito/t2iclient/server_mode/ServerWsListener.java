package com.github.us_aito.t2iclient.server_mode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@Slf4j
class ServerWsListener implements WebSocket.Listener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String trackingPromptId;
    private final CompletableFuture<Void> jobFuture;
    private final Consumer<Iterable<JsonNode>> imageConsumer;
    private final StringBuilder buffer = new StringBuilder();

    ServerWsListener(String trackingPromptId,
                     CompletableFuture<Void> jobFuture,
                     Consumer<Iterable<JsonNode>> imageConsumer) {
        this.trackingPromptId = trackingPromptId;
        this.jobFuture = jobFuture;
        this.imageConsumer = imageConsumer;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (!last) {
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
        String message = buffer.toString();
        buffer.setLength(0);

        JsonNode root;
        try {
            root = MAPPER.readTree(message);
        } catch (JsonProcessingException e) {
            log.error("WebSocket メッセージのパース失敗: {}", e.getMessage());
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        String type = root.path("type").asText("");
        String promptId = root.path("data").path("prompt_id").asText("");

        switch (type) {
            case "executed" -> {
                if (trackingPromptId.equals(promptId)) {
                    JsonNode images = root.path("data").path("output").path("images");
                    imageConsumer.accept(images);
                }
            }
            case "execution_success" -> {
                if (trackingPromptId.equals(promptId)) {
                    jobFuture.complete(null);
                }
            }
            case "execution_error" -> {
                if (trackingPromptId.equals(promptId)) {
                    jobFuture.completeExceptionally(
                        new ExecutionErrorException("ComfyUI execution_error for prompt: " + promptId));
                }
            }
            case "executing", "progress_state", "progress" ->
                log.info("ComfyUI イベント: type={}", type);
            default ->
                log.debug("未対応 WebSocket イベント: type={}", type);
        }

        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.warn("ComfyUI WebSocket クローズ: statusCode={}, reason={}", statusCode, reason);
        jobFuture.completeExceptionally(
            new WebSocketDisconnectedException("WebSocket closed: " + reason));
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("ComfyUI WebSocket エラー", error);
        jobFuture.completeExceptionally(
            new WebSocketDisconnectedException("WebSocket error: " + error.getMessage()));
    }

    static final class WebSocketDisconnectedException extends RuntimeException {
        WebSocketDisconnectedException(String message) {
            super(message);
        }
    }

    static final class ExecutionErrorException extends RuntimeException {
        ExecutionErrorException(String message) {
            super(message);
        }
    }
}
