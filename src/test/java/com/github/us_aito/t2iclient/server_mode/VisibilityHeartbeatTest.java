package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisibilityHeartbeatTest {

    @Test
    void start_callsChangeVisibilityAtInterval() throws InterruptedException {
        SqsJobReceiver mockReceiver = mock(SqsJobReceiver.class);
        // 間隔 100ms、延長 60 秒
        VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(mockReceiver, 100, 60);

        heartbeat.start("receipt-001");
        Thread.sleep(350);
        heartbeat.stop();

        // 100ms 間隔で 350ms 待つと最低 2 回は呼ばれるはず
        verify(mockReceiver, atLeast(2)).changeVisibility("receipt-001", 60);
    }

    @Test
    void stop_preventsSubsequentCalls() throws InterruptedException {
        SqsJobReceiver mockReceiver = mock(SqsJobReceiver.class);
        VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(mockReceiver, 100, 60);

        heartbeat.start("receipt-002");

        // 少なくとも 1 回 changeVisibility が登録されるまで決定論的に待つ（CI 環境差を吸収）
        verify(mockReceiver, timeout(2000).atLeastOnce())
            .changeVisibility("receipt-002", 60);

        heartbeat.stop();

        // cancel(false) は実行中タスクを中断しないので、in-flight タスクの登録完了を待つ
        Thread.sleep(200);

        int countAtStop = Mockito.mockingDetails(mockReceiver).getInvocations().size();

        // stop が効いていなければ period=100ms で複数回呼ばれるだけの時間を待つ
        Thread.sleep(500);

        int countAfterStop = Mockito.mockingDetails(mockReceiver).getInvocations().size();

        assertEquals(countAtStop, countAfterStop,
            "stop() 後は changeVisibility が追加で呼ばれてはならない");
    }

    @Test
    void exceptionInHeartbeat_doesNotStopHeartbeat() throws InterruptedException {
        SqsJobReceiver mockReceiver = mock(SqsJobReceiver.class);
        doThrow(new RuntimeException("SQS error"))
            .doNothing()
            .when(mockReceiver).changeVisibility(anyString(), anyInt());

        VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(mockReceiver, 100, 60);
        heartbeat.start("receipt-003");
        Thread.sleep(350);
        heartbeat.stop();

        // 最初の呼び出しは例外、その後も続くので合計 2 回以上呼ばれる
        verify(mockReceiver, atLeast(2)).changeVisibility("receipt-003", 60);
    }

    @Test
    void close_shutsDownExecutor() {
        SqsJobReceiver mockReceiver = mock(SqsJobReceiver.class);
        VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(mockReceiver, 1000, 60);
        assertDoesNotThrow(heartbeat::close);
    }
}
