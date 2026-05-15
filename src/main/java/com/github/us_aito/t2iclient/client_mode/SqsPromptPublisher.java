package com.github.us_aito.t2iclient.client_mode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Slf4j
public final class SqsPromptPublisher implements AutoCloseable {

    private static final Pattern QUEUE_URL_PATTERN = Pattern.compile(
        "^https://sqs\\.[a-z0-9-]+\\.amazonaws\\.com/\\d+/[A-Za-z0-9_.-]+(\\.fifo)?$"
    );
    private static final int MAX_MESSAGE_BYTES = 262144;

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsPromptPublisher(String queueUrl) {
        validateQueueUrl(queueUrl);
        this.queueUrl = queueUrl;
        this.sqsClient = SqsClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    SqsPromptPublisher(SqsClient sqsClient, String queueUrl) {
        validateQueueUrl(queueUrl);
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    private static void validateQueueUrl(String queueUrl) {
        if (queueUrl == null || !QUEUE_URL_PATTERN.matcher(queueUrl).matches()) {
            throw new InvalidQueueUrlException(
                "--sqs must be an SQS queue URL like https://sqs.<region>.amazonaws.com/<account>/<queue>, got: " + queueUrl
            );
        }
    }

    public void healthCheck() {
        try {
            sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .build());
            log.info("SQS health check passed for queue: {}", queueUrl);
        } catch (SqsException e) {
            if (e.statusCode() == 403) {
                log.warn("sqs:GetQueueAttributes 権限なし、ヘルスチェックをスキップ (queue: {})", queueUrl);
                return;
            }
            throw e;
        }
    }

    public String publish(String messageBody) {
        int byteSize = messageBody.getBytes(StandardCharsets.UTF_8).length;
        if (byteSize > MAX_MESSAGE_BYTES) {
            throw new MessageTooLongException(
                "Message body exceeds 256 KiB limit: " + byteSize + " bytes"
            );
        }
        var response = sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build());
        String messageId = response.messageId();
        log.info("Published to SQS: messageId={}, size={} bytes", messageId, byteSize);
        return messageId;
    }

    @Override
    public void close() {
        sqsClient.close();
    }

    public static final class InvalidQueueUrlException extends RuntimeException {
        public InvalidQueueUrlException(String message) {
            super(message);
        }
    }

    public static final class MessageTooLongException extends RuntimeException {
        public MessageTooLongException(String message) {
            super(message);
        }
    }
}
