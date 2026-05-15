package com.github.us_aito.t2iclient.server_mode;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class VisibilityHeartbeat implements AutoCloseable {

    private final SqsJobReceiver receiver;
    private final long intervalMs;
    private final int extensionSeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> currentTask;

    public VisibilityHeartbeat(SqsJobReceiver receiver) {
        this.receiver = receiver;
        this.intervalMs = 20_000L;
        this.extensionSeconds = 60;
    }

    /** テスト用: intervalMs はミリ秒単位で指定する。 */
    VisibilityHeartbeat(SqsJobReceiver receiver, long intervalMs, int extensionSeconds) {
        this.receiver = receiver;
        this.intervalMs = intervalMs;
        this.extensionSeconds = extensionSeconds;
    }

    public synchronized void start(String receiptHandle) {
        stop();
        currentTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                receiver.changeVisibility(receiptHandle, extensionSeconds);
                log.debug("可視性タイムアウトを延長: {}秒", extensionSeconds);
            } catch (Exception e) {
                log.warn("可視性タイムアウト延長に失敗（継続）: {}", e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdown();
    }
}
