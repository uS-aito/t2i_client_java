package com.github.us_aito.t2iclient.server_mode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class ComfyUiJobExecutor implements AutoCloseable {

    public static final String COMFYUI_ADDRESS = "127.0.0.1:8188";

    private final WorkflowManager workflowManager;
    private final S3ImageSink sink;
    private final S3Destination destination;
    private final HttpClient wsHttpClient;

    private static final ObjectMapper BODY_MAPPER = new ObjectMapper();

    private final AtomicReference<WebSocket> currentWebSocket = new AtomicReference<>();
    private volatile CompletableFuture<ExecutionResult> executionResultFuture;
    private volatile String connectedClientId;

    private final JobDispatchListener dispatchListener = new JobDispatchListener();

    public ComfyUiJobExecutor(WorkflowManager workflowManager, S3ImageSink sink, S3Destination destination) {
        this.workflowManager = workflowManager;
        this.sink = sink;
        this.destination = destination;
        this.wsHttpClient = HttpClient.newHttpClient();
    }

    ComfyUiJobExecutor(WorkflowManager workflowManager, S3ImageSink sink, S3Destination destination, HttpClient wsHttpClient) {
        this.workflowManager = workflowManager;
        this.sink = sink;
        this.destination = destination;
        this.wsHttpClient = wsHttpClient;
    }

    public void connect(String clientId) {
        String id = (clientId != null && !clientId.isEmpty())
            ? clientId
            : UUID.randomUUID().toString();
        this.connectedClientId = id;
        URI uri = URI.create("ws://" + COMFYUI_ADDRESS + "/ws?clientId=" + id);
        int[] delaysMs = {1000, 2000, 4000};

        for (int i = 0; i < 3; i++) {
            try {
                WebSocket ws = wsHttpClient.newWebSocketBuilder()
                    .buildAsync(uri, dispatchListener)
                    .join();
                currentWebSocket.set(ws);
                log.info("ComfyUI WebSocket 接続確立: {}", uri);
                return;
            } catch (Exception e) {
                log.warn("ComfyUI WebSocket 接続失敗 ({}/3): {}", i + 1, e.getMessage());
                if (i < 2) {
                    try {
                        Thread.sleep(delaysMs[i]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new WebSocketConnectException("接続が中断されました", ie);
                    }
                }
            }
        }
        throw new WebSocketConnectException("ComfyUI WebSocket に3回接続失敗しました", null);
    }

    public ExecutionResult execute(JobMessage jobMessage) {
        CompletableFuture<Void> executionFuture = new CompletableFuture<>();
        String promptId;
        try {
            String body = injectClientId(jobMessage.body(), connectedClientId);
            promptId = workflowManager.sendPrompt(COMFYUI_ADDRESS, body);
            log.info("ComfyUI 投入: SQS MessageId={}, prompt_id={}", jobMessage.messageId(), promptId);
        } catch (Exception e) {
            log.error("ComfyUI /prompt 送信失敗: {}", e.getMessage());
            return ExecutionResult.FAILURE_COMFYUI;
        }
        dispatchListener.setCurrentJob(jobMessage.messageId(), promptId, executionFuture);
        return awaitResult(executionFuture);
    }

    ExecutionResult execute(JobMessage jobMessage, CompletableFuture<Void> executionFuture) {
        try {
            String promptId = workflowManager.sendPrompt(COMFYUI_ADDRESS, jobMessage.body());
            log.info("ComfyUI 投入: SQS MessageId={}, prompt_id={}", jobMessage.messageId(), promptId);
        } catch (Exception e) {
            log.error("ComfyUI /prompt 送信失敗: {}", e.getMessage());
            return ExecutionResult.FAILURE_COMFYUI;
        }
        return awaitResult(executionFuture);
    }

    private ExecutionResult awaitResult(CompletableFuture<Void> executionFuture) {
        CompletableFuture<ExecutionResult> resultFuture = new CompletableFuture<>();
        this.executionResultFuture = resultFuture;

        executionFuture.whenComplete((v, ex) -> {
            if (!resultFuture.isDone()) {
                if (ex == null) {
                    resultFuture.complete(ExecutionResult.SUCCESS);
                } else if (ex instanceof ServerWsListener.WebSocketDisconnectedException) {
                    resultFuture.complete(ExecutionResult.FAILURE_WS_DISCONNECTED);
                } else {
                    resultFuture.complete(ExecutionResult.FAILURE_COMFYUI);
                }
            }
        });

        return resultFuture.join();
    }

    void handleExecuted(String messageId, String promptId, String filename, String imageType, String subFolder) {
        String objectKey = buildObjectKey(messageId, subFolder, filename);
        try {
            byte[] bytes = workflowManager.fetchImage(COMFYUI_ADDRESS, filename, imageType, subFolder);
            sink.put(objectKey, bytes);
            log.info("S3 アップロード完了: {}", objectKey);
            CompletableFuture<ExecutionResult> rf = executionResultFuture;
            if (rf != null) {
                rf.complete(ExecutionResult.SUCCESS);
            }
        } catch (Exception e) {
            log.error("S3 アップロード失敗: {}", e.getMessage());
            CompletableFuture<ExecutionResult> rf = executionResultFuture;
            if (rf != null) {
                rf.complete(ExecutionResult.FAILURE_S3);
            }
        }
    }

    private String injectClientId(String rawBody, String clientId) throws IOException {
        ObjectNode root = (ObjectNode) BODY_MAPPER.readTree(rawBody);
        root.put("client_id", clientId);
        return BODY_MAPPER.writeValueAsString(root);
    }

    private String buildObjectKey(String messageId, String subFolder, String filename) {
        String relative = (subFolder == null || subFolder.isEmpty())
            ? messageId + "/" + filename
            : messageId + "/" + subFolder + "/" + filename;
        return destination.buildKey(relative);
    }

    @Override
    public void close() {
        WebSocket ws = currentWebSocket.get();
        if (ws != null) {
            ws.abort();
        }
    }

    public enum ExecutionResult { SUCCESS, FAILURE_COMFYUI, FAILURE_S3, FAILURE_WS_DISCONNECTED }

    public static final class WebSocketConnectException extends RuntimeException {
        public WebSocketConnectException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private class JobDispatchListener implements WebSocket.Listener {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private volatile String currentMessageId;
        private volatile String trackingPromptId;
        private volatile CompletableFuture<Void> jobFuture;
        private final StringBuilder buffer = new StringBuilder();

        void setCurrentJob(String messageId, String promptId, CompletableFuture<Void> future) {
            this.currentMessageId = messageId;
            this.trackingPromptId = promptId;
            this.jobFuture = future;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (!last) return WebSocket.Listener.super.onText(webSocket, data, last);

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
            String tracked = trackingPromptId;

            switch (type) {
                case "executed" -> {
                    log.info("WebSocket executed 受信: promptId={}, tracked={}", promptId, tracked);
                    if (tracked != null && tracked.equals(promptId)) {
                        JsonNode images = root.path("data").path("output").path("images");
                        String msgId = currentMessageId;
                        for (JsonNode image : images) {
                            handleExecuted(
                                msgId,
                                promptId,
                                image.path("filename").asText(""),
                                image.path("type").asText(""),
                                image.path("subfolder").asText("")
                            );
                        }
                    } else {
                        log.warn("executed: prompt_id 不一致 (tracked={}, received={})", tracked, promptId);
                    }
                }
                case "execution_success" -> {
                    log.info("WebSocket execution_success 受信: promptId={}, tracked={}", promptId, tracked);
                    if (tracked != null && tracked.equals(promptId)) {
                        CompletableFuture<Void> f = jobFuture;
                        if (f != null) f.complete(null);
                    } else {
                        log.warn("execution_success: prompt_id 不一致 (tracked={}, received={})", tracked, promptId);
                    }
                }
                case "execution_error" -> {
                    if (tracked != null && tracked.equals(promptId)) {
                        CompletableFuture<Void> f = jobFuture;
                        if (f != null) f.completeExceptionally(
                            new ServerWsListener.ExecutionErrorException("ComfyUI execution_error: " + promptId));
                    }
                }
                case "executing", "progress_state", "progress" ->
                    log.info("ComfyUI イベント: type={}", type);
                default -> log.debug("未対応 WebSocket イベント: type={}", type);
            }

            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("ComfyUI WebSocket クローズ: statusCode={}, reason={}", statusCode, reason);
            CompletableFuture<Void> f = jobFuture;
            if (f != null) f.completeExceptionally(
                new ServerWsListener.WebSocketDisconnectedException("WebSocket closed: " + reason));
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("ComfyUI WebSocket エラー", error);
            CompletableFuture<Void> f = jobFuture;
            if (f != null) f.completeExceptionally(
                new ServerWsListener.WebSocketDisconnectedException("WebSocket error: " + error.getMessage()));
        }
    }
}
