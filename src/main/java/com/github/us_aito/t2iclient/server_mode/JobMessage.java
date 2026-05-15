package com.github.us_aito.t2iclient.server_mode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record JobMessage(
    String messageId,
    String receiptHandle,
    String body,
    String clientId
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JobMessage parse(String messageId, String receiptHandle, String body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new ParseException("Failed to parse SQS message body: " + e.getMessage());
        }
        if (root.path("prompt").isMissingNode()) {
            throw new ParseException("SQS message body missing required field: prompt");
        }
        String clientId = root.path("client_id").asText(null);
        return new JobMessage(messageId, receiptHandle, body, clientId);
    }

    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
