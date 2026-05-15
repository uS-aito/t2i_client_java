package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SqsJobReceiverTest {

    private static final String VALID_URL = "https://sqs.ap-northeast-1.amazonaws.com/123456789012/my-queue";
    private static final String VALID_BODY = "{\"client_id\":\"c1\",\"prompt\":{\"1\":{}}}";

    @Mock
    private SqsClient mockSqsClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // --- URL 検証 ---

    @Test
    void constructor_validUrl_noException() {
        assertDoesNotThrow(() -> new SqsJobReceiver(mockSqsClient, VALID_URL));
    }

    @Test
    void constructor_invalidUrl_throws() {
        assertThrows(SqsJobReceiver.InvalidQueueUrlException.class,
            () -> new SqsJobReceiver(mockSqsClient, "not-a-url"));
    }

    // --- receiveOne: メッセージあり ---

    @Test
    void receiveOne_messageReceived_returnsJobMessage() {
        Message msg = Message.builder()
            .messageId("msg-001")
            .receiptHandle("receipt-001")
            .body(VALID_BODY)
            .build();
        when(mockSqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(msg)).build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        Optional<JobMessage> result = receiver.receiveOne();

        assertTrue(result.isPresent());
        assertEquals("msg-001", result.get().messageId());
        assertEquals("receipt-001", result.get().receiptHandle());
        assertEquals(VALID_BODY, result.get().body());
    }

    @Test
    void receiveOne_noMessages_returnsEmpty() {
        when(mockSqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        Optional<JobMessage> result = receiver.receiveOne();

        assertTrue(result.isEmpty());
    }

    @Test
    void receiveOne_usesLongPollingParameters() {
        when(mockSqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        receiver.receiveOne();

        verify(mockSqsClient).receiveMessage(argThat((ReceiveMessageRequest req) ->
            req.maxNumberOfMessages() == 1
            && req.waitTimeSeconds() == 20
            && req.queueUrl().equals(VALID_URL)
        ));
    }

    // --- delete ---

    @Test
    void delete_callsDeleteMessageWithCorrectParams() {
        when(mockSqsClient.deleteMessage(any(DeleteMessageRequest.class)))
            .thenReturn(DeleteMessageResponse.builder().build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        receiver.delete("receipt-handle-123");

        verify(mockSqsClient).deleteMessage(argThat((DeleteMessageRequest req) ->
            req.queueUrl().equals(VALID_URL)
            && req.receiptHandle().equals("receipt-handle-123")
        ));
    }

    // --- changeVisibility ---

    @Test
    void changeVisibility_callsApiWithCorrectParams() {
        when(mockSqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
            .thenReturn(ChangeMessageVisibilityResponse.builder().build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        receiver.changeVisibility("receipt-handle-abc", 60);

        verify(mockSqsClient).changeMessageVisibility(argThat((ChangeMessageVisibilityRequest req) ->
            req.queueUrl().equals(VALID_URL)
            && req.receiptHandle().equals("receipt-handle-abc")
            && req.visibilityTimeout() == 60
        ));
    }

    // --- healthCheck ---

    @Test
    void healthCheck_success_noException() {
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenReturn(GetQueueAttributesResponse.builder().build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        assertDoesNotThrow(receiver::healthCheck);
    }

    @Test
    void healthCheck_accessDenied_continuesWithoutException() {
        SqsException accessDenied = (SqsException) SqsException.builder()
            .statusCode(403).message("Access Denied").build();
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(accessDenied);

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        assertDoesNotThrow(receiver::healthCheck);
    }

    @Test
    void healthCheck_queueDoesNotExist_propagatesException() {
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(QueueDoesNotExistException.builder().message("Queue not found").build());

        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        assertThrows(QueueDoesNotExistException.class, receiver::healthCheck);
    }

    // --- close ---

    @Test
    void close_delegatesToSqsClient() {
        SqsJobReceiver receiver = new SqsJobReceiver(mockSqsClient, VALID_URL);
        receiver.close();
        verify(mockSqsClient).close();
    }
}
