package com.github.us_aito.t2iclient.server_mode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
public final class SqsJobReceiver implements AutoCloseable {

    private static final Pattern QUEUE_URL_PATTERN = Pattern.compile(
        "^https://sqs\\.[a-z0-9-]+\\.amazonaws\\.com/\\d+/[A-Za-z0-9_.-]+(\\.fifo)?$"
    );

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsJobReceiver(String queueUrl) {
        validateQueueUrl(queueUrl);
        this.queueUrl = queueUrl;
        this.sqsClient = SqsClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    SqsJobReceiver(SqsClient sqsClient, String queueUrl) {
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

    public Optional<JobMessage> receiveOne() {
        ReceiveMessageResponse response = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20)
                .build()
        );
        List<Message> messages = response.messages();
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        Message m = messages.get(0);
        JobMessage jobMessage = JobMessage.parse(m.messageId(), m.receiptHandle(), m.body());
        return Optional.of(jobMessage);
    }

    public void delete(String receiptHandle) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build());
    }

    public void changeVisibility(String receiptHandle, int visibilityTimeoutSeconds) {
        sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(visibilityTimeoutSeconds)
            .build());
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
}
