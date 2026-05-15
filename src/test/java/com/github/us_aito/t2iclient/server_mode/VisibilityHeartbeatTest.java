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
        Thread.sleep(150);
        heartbeat.stop();

        int countAtStop = Mockito.mockingDetails(mockReceiver).getInvocations().size();
        Thread.sleep(200);
        int countAfterStop = Mockito.mockingDetails(mockReceiver).getInvocations().size();

        assertEquals(countAtStop, countAfterStop);
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
