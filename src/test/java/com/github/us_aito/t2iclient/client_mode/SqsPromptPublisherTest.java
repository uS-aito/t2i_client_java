package com.github.us_aito.t2iclient.client_mode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SqsPromptPublisherTest {

    private static final String VALID_URL = "https://sqs.ap-northeast-1.amazonaws.com/123456789012/my-queue";

    @Mock
    private SqsClient mockSqsClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // --- URL 検証 ---

    @Test
    void constructor_validUrl_noException() {
        assertDoesNotThrow(() -> new SqsPromptPublisher(mockSqsClient, VALID_URL));
    }

    @Test
    void constructor_invalidUrl_throwsInvalidQueueUrlException() {
        assertThrows(
            SqsPromptPublisher.InvalidQueueUrlException.class,
            () -> new SqsPromptPublisher(mockSqsClient, "not-a-valid-url")
        );
    }

    @Test
    void constructor_sqsArnInsteadOfUrl_throwsInvalidQueueUrlException() {
        assertThrows(
            SqsPromptPublisher.InvalidQueueUrlException.class,
            () -> new SqsPromptPublisher(mockSqsClient, "arn:aws:sqs:ap-northeast-1:123:queue")
        );
    }

    @Test
    void constructor_fifoUrl_noException() {
        String fifoUrl = "https://sqs.ap-northeast-1.amazonaws.com/123456789012/my-queue.fifo";
        assertDoesNotThrow(() -> new SqsPromptPublisher(mockSqsClient, fifoUrl));
    }

    // --- publish ---

    @Test
    void publish_smallBody_callsSendMessageWithBodyOnly() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        String messageId = "msg-001";
        when(mockSqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenReturn(SendMessageResponse.builder().messageId(messageId).build());

        String result = publisher.publish("hello");

        assertEquals(messageId, result);
        verify(mockSqsClient).sendMessage(argThat((SendMessageRequest req) ->
            req.messageBody().equals("hello")
            && req.queueUrl().equals(VALID_URL)
            && (req.messageAttributes() == null || req.messageAttributes().isEmpty())
        ));
    }

    @Test
    void publish_bodyExceeds256KiB_throwsMessageTooLongException() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        String oversizedBody = "x".repeat(262145);

        assertThrows(
            SqsPromptPublisher.MessageTooLongException.class,
            () -> publisher.publish(oversizedBody)
        );
        verifyNoInteractions(mockSqsClient);
    }

    @Test
    void publish_bodyExactly256KiB_succeeds() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        // 262144 bytes exactly = 256 KiB (ASCII = 1 byte per char)
        String body = "x".repeat(262144);
        when(mockSqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenReturn(SendMessageResponse.builder().messageId("id").build());

        assertDoesNotThrow(() -> publisher.publish(body));
    }

    // --- close ---

    @Test
    void close_delegatesToSqsClient() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        publisher.close();
        verify(mockSqsClient).close();
    }

    // --- healthCheck ---

    @Test
    void healthCheck_success_noException() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenReturn(GetQueueAttributesResponse.builder().build());

        assertDoesNotThrow(publisher::healthCheck);
    }

    @Test
    void healthCheck_accessDenied_continuesWithoutException() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        SqsException accessDenied = (SqsException) SqsException.builder()
            .statusCode(403)
            .message("Access Denied")
            .build();
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(accessDenied);

        assertDoesNotThrow(publisher::healthCheck);
    }

    @Test
    void healthCheck_queueDoesNotExist_propagatesException() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(QueueDoesNotExistException.builder().message("Queue not found").build());

        assertThrows(QueueDoesNotExistException.class, publisher::healthCheck);
    }

    @Test
    void healthCheck_sdkClientException_propagatesException() {
        SqsPromptPublisher publisher = new SqsPromptPublisher(mockSqsClient, VALID_URL);
        when(mockSqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(SdkClientException.builder().message("No credentials").build());

        assertThrows(SdkClientException.class, publisher::healthCheck);
    }
}
