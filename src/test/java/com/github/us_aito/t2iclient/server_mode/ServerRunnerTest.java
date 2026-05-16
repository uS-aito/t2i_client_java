package com.github.us_aito.t2iclient.server_mode;

import com.github.us_aito.t2iclient.cli.AppArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerRunnerTest {

    private static final String VALID_BODY = "{\"client_id\":\"c1\",\"prompt\":{\"1\":{}}}";
    private static final AppArgs FAKE_ARGS = new AppArgs(
        AppArgs.AppMode.SERVER, null,
        "https://sqs.ap-northeast-1.amazonaws.com/123/q",
        "s3://my-bucket/results"
    );

    @Mock SqsJobReceiver mockReceiver;
    @Mock ComfyUiJobExecutor mockExecutor;
    @Mock S3ImageSink mockSink;
    @Mock VisibilityHeartbeat mockHeartbeat;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ServerRunner newRunner() {
        return new ServerRunner(mockReceiver, mockExecutor, mockSink, mockHeartbeat);
    }

    private JobMessage job(String id) {
        return JobMessage.parse(id, "receipt-" + id, VALID_BODY);
    }

    // --- 正常ジョブ完了で delete が呼ばれる ---

    @Test
    void run_successfulJob_deletesMessage() throws Exception {
        JobMessage job = job("m1");
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doNothing().when(mockExecutor).connect(any());

        when(mockReceiver.receiveOne())
            .thenReturn(Optional.of(job))
            .thenAnswer(inv -> { runner.requestShutdown(); return Optional.empty(); });
        when(mockExecutor.execute(job)).thenReturn(ComfyUiJobExecutor.ExecutionResult.SUCCESS);

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.SUCCESS, code);
        verify(mockReceiver).delete("receipt-m1");
    }

    // --- execute が失敗の場合は delete が呼ばれない ---

    @Test
    void run_failedJob_doesNotDeleteMessage() throws Exception {
        JobMessage job = job("m2");
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doNothing().when(mockExecutor).connect(any());

        when(mockReceiver.receiveOne())
            .thenReturn(Optional.of(job))
            .thenAnswer(inv -> { runner.requestShutdown(); return Optional.empty(); });
        when(mockExecutor.execute(job)).thenReturn(ComfyUiJobExecutor.ExecutionResult.FAILURE_COMFYUI);

        runner.run(FAKE_ARGS);

        verify(mockReceiver, never()).delete(any());
    }

    // --- execute が例外を投げた場合も delete が呼ばれない ---

    @Test
    void run_executeThrows_doesNotDeleteMessage() throws Exception {
        JobMessage job = job("m3");
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doNothing().when(mockExecutor).connect(any());

        when(mockReceiver.receiveOne())
            .thenReturn(Optional.of(job))
            .thenAnswer(inv -> { runner.requestShutdown(); return Optional.empty(); });
        when(mockExecutor.execute(job)).thenThrow(new RuntimeException("unexpected error"));

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.SUCCESS, code);
        verify(mockReceiver, never()).delete(any());
    }

    // --- ParseException: ログ出力＋非削除でループ継続 ---

    @Test
    void run_parseException_doesNotDeleteAndContinues() throws Exception {
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doNothing().when(mockExecutor).connect(any());

        when(mockReceiver.receiveOne())
            .thenThrow(new JobMessage.ParseException("bad JSON"))
            .thenAnswer(inv -> { runner.requestShutdown(); return Optional.empty(); });

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.SUCCESS, code);
        verify(mockReceiver, never()).delete(any());
    }

    // --- shutdownRequested 後にループ終了して SUCCESS ---

    @Test
    void run_shutdownAfterEmptyReceive_returnsSuccess() throws Exception {
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doNothing().when(mockExecutor).connect(any());

        when(mockReceiver.receiveOne()).thenAnswer(inv -> {
            runner.requestShutdown();
            return Optional.empty();
        });

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.SUCCESS, code);
    }

    // --- 起動時 SQS ヘルスチェック失敗で AUTH_ERROR ---

    @Test
    void run_sqsHealthCheckFails_returnsAuthError() throws Exception {
        ServerRunner runner = newRunner();

        doThrow(new RuntimeException("SQS auth failed")).when(mockReceiver).healthCheck();

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.AUTH_ERROR, code);
        verify(mockReceiver, never()).receiveOne();
    }

    // --- ComfyUI 接続失敗で COMFYUI_ERROR ---

    @Test
    void run_comfyuiConnectFails_returnsComfyuiError() throws Exception {
        ServerRunner runner = newRunner();

        doNothing().when(mockReceiver).healthCheck();
        doNothing().when(mockSink).healthCheck();
        doThrow(new ComfyUiJobExecutor.WebSocketConnectException("connect failed", null))
            .when(mockExecutor).connect(any());

        ServerExitCode code = runner.run(FAKE_ARGS);

        assertEquals(ServerExitCode.COMFYUI_ERROR, code);
    }
}
