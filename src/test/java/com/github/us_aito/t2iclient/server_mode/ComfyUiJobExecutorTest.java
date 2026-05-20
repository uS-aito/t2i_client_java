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

    private static final S3Destination DEST = S3Destination.parse("s3://my-bucket/results");

    @Mock WorkflowManager mockWorkflowManager;
    @Mock S3ImageSink mockS3Sink;
    @Mock HttpClient mockHttpClient;
    @Mock WebSocket mockWebSocket;

    private static String body(String project, String scene, String serial, int batchIndex) {
        return "{\"project_name\":\"" + project + "\","
            + "\"scene_name\":\"" + scene + "\","
            + "\"serial\":\"" + serial + "\","
            + "\"batch_index\":" + batchIndex + ","
            + "\"comfyui_payload\":{\"client_id\":\"c1\",\"prompt\":{\"1\":{}}}}";
    }

    private JobMessage makeJob(String msgId) {
        return JobMessage.parse(msgId, "receipt-" + msgId, body("proj", "scene1", "20260520-143022", 0));
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
        JobMessage job = makeJob("m4");
        CompletableFuture<ComfyUiJobExecutor.ExecutionResult> resultFuture =
            CompletableFuture.supplyAsync(() -> executor.execute(job, future));

        // 少し待ってから S3 失敗を含む executed ペイロードを発火
        Thread.sleep(50);
        executor.handleExecuted(job, "pid-004", "img.png", "output", "");

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

    // --- buildObjectKey: 新形式キー組み立て ---

    @Test
    void buildObjectKey_buildsExpectedFormat_withPrefix() {
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("myproject", "scene1", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "image.png");

        assertEquals("results/myproject/20260520-143022/scene1_00.png", key);
    }

    @Test
    void buildObjectKey_batchIndex_zeroPaddedToTwoDigits() {
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 5));

        String key = executor.buildObjectKey(job, "x.png");

        assertEquals("results/p/20260520-143022/s_05.png", key);
    }

    @Test
    void buildObjectKey_filenameWithoutExtension_fallsBackToPng() {
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "noext");

        assertEquals("results/p/20260520-143022/s_00.png", key);
    }

    @Test
    void buildObjectKey_filenameWithTrailingDot_fallsBackToPng() {
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "name.");

        assertEquals("results/p/20260520-143022/s_00.png", key);
    }

    @Test
    void buildObjectKey_filenameWithWebpExtension_preservesExtension() {
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "img.webp");

        assertEquals("results/p/20260520-143022/s_00.webp", key);
    }

    @Test
    void buildObjectKey_withoutPrefix_omitsPrefixSegment() {
        S3Destination destNoPrefix = S3Destination.parse("s3://my-bucket");
        ComfyUiJobExecutor executor =
            new ComfyUiJobExecutor(mockWorkflowManager, mockS3Sink, destNoPrefix, mockHttpClient);
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "x.png");

        assertEquals("p/20260520-143022/s_00.png", key);
    }

    // --- buildObjectKey: sanitize 二重適用 (defense-in-depth) ---

    @Test
    void buildObjectKey_sceneNameWithPathEscape_isSanitizedAgainOnServer() {
        ComfyUiJobExecutor executor = newExecutor();
        // Client が万一サニタイズせずに送ったメッセージ。"../escape" の '/' は Server 側で '_' に再置換される。
        JobMessage job = JobMessage.parse("msg", "r", body("proj", "../escape", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "x.png");

        // serial までの 2 つの '/' を除き、scene 部分にパス区切りが含まれないことを確認
        assertEquals("results/proj/20260520-143022/.._escape_00.png", key);
        // serial フォルダ以降にさらに '/' が増えていないこと
        String afterSerial = key.substring(key.indexOf("20260520-143022/") + "20260520-143022/".length());
        assertFalse(afterSerial.contains("/"), "scene 部分にパス区切りが含まれてはいけない: " + afterSerial);
    }

    @Test
    void buildObjectKey_projectAndSceneWithUnsafeChars_areSanitized() {
        ComfyUiJobExecutor executor = newExecutor();
        // Server 側 sanitize 二重適用により、空白・記号は '_' に置換される。
        JobMessage job = JobMessage.parse("msg", "r", body("p:roj", "sc ene*1", "20260520-143022", 0));

        String key = executor.buildObjectKey(job, "x.png");

        assertEquals("results/p_roj/20260520-143022/sc_ene_1_00.png", key);
    }

    // --- handleExecuted: subfolder を無視する ---

    @Test
    void handleExecuted_ignoresSubfolderInKey() throws Exception {
        when(mockWorkflowManager.fetchImage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new byte[]{1, 2, 3});
        ComfyUiJobExecutor executor = newExecutor();
        JobMessage job = JobMessage.parse("msg", "r", body("p", "s", "20260520-143022", 0));

        executor.handleExecuted(job, "pid", "out.png", "output", "nested/sub");

        verify(mockS3Sink).put(eq("results/p/20260520-143022/s_00.png"), any());
    }
}
