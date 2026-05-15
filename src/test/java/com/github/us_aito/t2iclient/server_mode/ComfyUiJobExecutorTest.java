package com.github.us_aito.t2iclient.server_mode;

import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComfyUiJobExecutorTest {

    private static final String VALID_BODY = "{\"client_id\":\"c1\",\"prompt\":{\"1\":{}}}";
    private static final S3Destination DEST = S3Destination.parse("s3://my-bucket/results");

    @Mock WorkflowManager mockWorkflowManager;
    @Mock S3ImageSink mockS3Sink;
    @Mock HttpClient mockHttpClient;
    @Mock WebSocket mockWebSocket;

    private JobMessage makeJob(String msgId) {
        return JobMessage.parse(msgId, "receipt-" + msgId, VALID_BODY);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ComfyUiJobExecutor newExecutor() {
        return new ComfyUiJobExecutor(mockWorkflowManager, mockS3Sink, DEST, mockHttpClient);
    }

    // --- execute: 成功（Future を事前完了） ---

    @Test
    void execute_success_returnsSuccess() throws Exception {
        when(mockWorkflowManager.sendPrompt(anyString(), anyString())).thenReturn("pid-001");
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        ComfyUiJobExecutor executor = newExecutor();
        ComfyUiJobExecutor.ExecutionResult result = executor.execute(makeJob("m1"), future);

        assertEquals(ComfyUiJobExecutor.ExecutionResult.SUCCESS, result);
    }

    // --- execute: execution_error → FAILURE_COMFYUI ---

    @Test
    void execute_executionError_returnsFailureComfyui() throws Exception {
        when(mockWorkflowManager.sendPrompt(anyString(), anyString())).thenReturn("pid-002");
        CompletableFuture<Void> future = CompletableFuture.failedFuture(
            new ServerWsListener.ExecutionErrorException("error"));

        ComfyUiJobExecutor executor = newExecutor();
        ComfyUiJobExecutor.ExecutionResult result = executor.execute(makeJob("m2"), future);

        assertEquals(ComfyUiJobExecutor.ExecutionResult.FAILURE_COMFYUI, result);
    }

    // --- execute: WS 切断 → FAILURE_WS_DISCONNECTED ---

    @Test
    void execute_wsDisconnected_returnsFailureWsDisconnected() throws Exception {
        when(mockWorkflowManager.sendPrompt(anyString(), anyString())).thenReturn("pid-003");
        CompletableFuture<Void> future = CompletableFuture.failedFuture(
            new ServerWsListener.WebSocketDisconnectedException("disconnected"));

        ComfyUiJobExecutor executor = newExecutor();
        ComfyUiJobExecutor.ExecutionResult result = executor.execute(makeJob("m3"), future);

        assertEquals(ComfyUiJobExecutor.ExecutionResult.FAILURE_WS_DISCONNECTED, result);
    }

    // --- execute: S3 失敗 → FAILURE_S3 ---

    @Test
    void execute_s3Failure_returnsFailureS3() throws Exception {
        when(mockWorkflowManager.sendPrompt(anyString(), anyString())).thenReturn("pid-004");
        when(mockWorkflowManager.fetchImage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new byte[]{1, 2, 3});
        doThrow(new RuntimeException("S3 error")).when(mockS3Sink).put(anyString(), any());

        // executed イベントを模倣するため、S3 upload を含むコールバック付き future
        CompletableFuture<Void> future = new CompletableFuture<>();
        ComfyUiJobExecutor executor = newExecutor();

        // 別スレッドで execute を実行
        CompletableFuture<ComfyUiJobExecutor.ExecutionResult> resultFuture =
            CompletableFuture.supplyAsync(() -> executor.execute(makeJob("m4"), future));

        // 少し待ってから S3 失敗を含む executed ペイロードを発火
        Thread.sleep(50);
        executor.handleExecuted("m4", "pid-004", "img.png", "output", "");

        ComfyUiJobExecutor.ExecutionResult result = resultFuture.get();
        assertEquals(ComfyUiJobExecutor.ExecutionResult.FAILURE_S3, result);
    }

    // --- connect: 3 回失敗で WebSocketConnectException ---

    @Test
    void connect_alwaysFails_throwsWebSocketConnectException() {
        WebSocket.Builder failingBuilder = mock(WebSocket.Builder.class);
        when(failingBuilder.buildAsync(any(URI.class), any(WebSocket.Listener.class)))
            .thenReturn(CompletableFuture.failedFuture(new IOException("refused")));
        when(mockHttpClient.newWebSocketBuilder()).thenReturn(failingBuilder);

        ComfyUiJobExecutor executor = newExecutor();
        assertThrows(ComfyUiJobExecutor.WebSocketConnectException.class, () -> executor.connect("c1"));
        verify(failingBuilder, times(3)).buildAsync(any(URI.class), any(WebSocket.Listener.class));
    }
}
