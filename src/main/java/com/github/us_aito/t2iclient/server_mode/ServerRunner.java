package com.github.us_aito.t2iclient.server_mode;

import com.github.us_aito.t2iclient.cli.AppArgs;
import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class ServerRunner {

    private final SqsJobReceiver receiverOverride;
    private final ComfyUiJobExecutor executorOverride;
    private final S3ImageSink sinkOverride;
    private final VisibilityHeartbeat heartbeatOverride;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public ServerRunner() {
        this.receiverOverride = null;
        this.executorOverride = null;
        this.sinkOverride = null;
        this.heartbeatOverride = null;
    }

    ServerRunner(SqsJobReceiver receiver, ComfyUiJobExecutor executor,
                 S3ImageSink sink, VisibilityHeartbeat heartbeat) {
        this.receiverOverride = receiver;
        this.executorOverride = executor;
        this.sinkOverride = sink;
        this.heartbeatOverride = heartbeat;
    }

    void requestShutdown() {
        shutdownRequested.set(true);
    }

    public ServerExitCode run(AppArgs appArgs) {
        SqsJobReceiver receiver;
        ComfyUiJobExecutor executor;
        S3ImageSink sink;
        VisibilityHeartbeat heartbeat;

        if (receiverOverride != null) {
            receiver = receiverOverride;
            executor = executorOverride;
            sink = sinkOverride;
            heartbeat = heartbeatOverride;
        } else {
            S3Destination destination = S3Destination.parse(appArgs.s3DestinationUri());
            receiver = new SqsJobReceiver(appArgs.sqsQueueUrl());
            sink = new S3ImageSink(destination);
            WorkflowManager wm = new WorkflowManager();
            executor = new ComfyUiJobExecutor(wm, sink, destination);
            heartbeat = new VisibilityHeartbeat(receiver);
        }

        return doRun(appArgs, receiver, executor, sink, heartbeat);
    }

    private ServerExitCode doRun(AppArgs appArgs, SqsJobReceiver receiver,
                                 ComfyUiJobExecutor executor, S3ImageSink sink,
                                 VisibilityHeartbeat heartbeat) {
        try {
            receiver.healthCheck();
        } catch (Exception e) {
            log.error("SQS ヘルスチェック失敗: {}", e.getMessage());
            return ServerExitCode.AUTH_ERROR;
        }

        try {
            executor.connect(null);
        } catch (ComfyUiJobExecutor.WebSocketConnectException e) {
            log.error("ComfyUI WebSocket 接続失敗: {}", e.getMessage());
            return ServerExitCode.COMFYUI_ERROR;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("シグナル受信: サーバーモード停止要求");
            shutdownRequested.set(true);
        }, "server-shutdown"));

        log.info("サーバーモード起動: queueUrl={}, s3={}", appArgs.sqsQueueUrl(), appArgs.s3DestinationUri());

        boolean drained = false;
        try {
            while (!shutdownRequested.get()) {
                Optional<JobMessage> jobOpt;
                try {
                    jobOpt = receiver.receiveOne();
                } catch (JobMessage.ParseException e) {
                    log.error("メッセージのパース失敗（非削除・スキップ）: {}", e.getMessage());
                    continue;
                } catch (SqsException e) {
                    log.error("SQS 受信でエラー発生、サーバーモードを停止します: {}", e.getMessage());
                    break;
                }

                if (jobOpt.isEmpty()) {
                    log.info("SQS キューが空のため処理を終了します");
                    drained = true;
                    break;
                }

                JobMessage job = jobOpt.get();
                log.info("SQS メッセージ受信: messageId={}, comfyuiPayloadSize={}", job.messageId(), job.body().length());

                try {
                    heartbeat.start(job.receiptHandle());
                    ComfyUiJobExecutor.ExecutionResult result;
                    try {
                        result = executor.execute(job);
                    } finally {
                        heartbeat.stop();
                    }

                    if (result == ComfyUiJobExecutor.ExecutionResult.SUCCESS) {
                        receiver.delete(job.receiptHandle());
                        log.info("ジョブ完了・メッセージ削除: messageId={}", job.messageId());
                    } else {
                        log.warn("ジョブ失敗（メッセージ非削除）: messageId={}, result={}", job.messageId(), result);
                    }
                } catch (Exception e) {
                    log.error("ジョブ処理でエラー発生（非削除・継続）: messageId={}, error={}",
                        job.messageId(), e.getMessage());
                }
            }
        } finally {
            closeQuietly("VisibilityHeartbeat", heartbeat);
            closeQuietly("ComfyUiJobExecutor", executor);
            closeQuietly("SqsJobReceiver", receiver);
        }

        if (drained) {
            log.info("SQS の全メッセージを処理しました");
        }
        log.info("サーバーモード正常停止");
        return ServerExitCode.SUCCESS;
    }

    private static void closeQuietly(String label, AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.warn("{} の close に失敗: {}", label, e.getMessage());
        }
    }
}
