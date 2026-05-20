package com.github.us_aito.t2iclient.server_mode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Server モードで処理する SQS ジョブメッセージ。
 *
 * <p>{@code body} フィールドは新形式 SQS メッセージの内側
 * {@code comfyui_payload} の JSON 文字列を保持する (トップレベル JSON ではない)。
 * これは {@code ComfyUiJobExecutor.injectClientId(body, ...)} がそのまま
 * ComfyUI に転送できる形を維持するための意味再定義。
 */
public record JobMessage(
    String messageId,
    String receiptHandle,
    String body,
    String projectName,
    String sceneName,
    String serial,
    int batchIndex,
    String clientId
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JobMessage parse(String messageId, String receiptHandle, String rawBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(rawBody);
        } catch (Exception e) {
            throw new ParseException(
                "SQS メッセージの JSON 解析に失敗しました (新形式 SQS スキーマを期待): " + e.getMessage()
            );
        }
        if (!root.isObject()) {
            throw new ParseException(
                "SQS メッセージは新形式 JSON オブジェクトである必要があります (旧形式または非オブジェクト)"
            );
        }

        String projectName = requireTextField(root, "project_name");
        String sceneName = requireTextField(root, "scene_name");
        String serial = requireTextField(root, "serial");
        int batchIndex = requireBatchIndex(root);

        JsonNode payloadNode = root.path("comfyui_payload");
        if (payloadNode.isMissingNode() || !payloadNode.isObject()) {
            throw new ParseException(
                "SQS メッセージに必須フィールド 'comfyui_payload' (オブジェクト) がありません (新形式 SQS スキーマを期待)"
            );
        }
        if (payloadNode.path("prompt").isMissingNode()) {
            throw new ParseException(
                "SQS メッセージの 'comfyui_payload.prompt' フィールドがありません (新形式 SQS スキーマを期待)"
            );
        }

        String clientId = payloadNode.path("client_id").isMissingNode()
            ? null
            : payloadNode.path("client_id").asText(null);

        String innerBody;
        try {
            innerBody = MAPPER.writeValueAsString(payloadNode);
        } catch (Exception e) {
            throw new ParseException(
                "comfyui_payload の再シリアライズに失敗しました: " + e.getMessage()
            );
        }

        return new JobMessage(
            messageId,
            receiptHandle,
            innerBody,
            projectName,
            sceneName,
            serial,
            batchIndex,
            clientId
        );
    }

    private static String requireTextField(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull() || !node.isTextual()) {
            throw new ParseException(
                "SQS メッセージに必須フィールド '" + fieldName + "' (文字列) がありません (新形式 SQS スキーマを期待)"
            );
        }
        return node.asText();
    }

    private static int requireBatchIndex(JsonNode root) {
        JsonNode node = root.path("batch_index");
        if (node.isMissingNode() || node.isNull()) {
            throw new ParseException(
                "SQS メッセージに必須フィールド 'batch_index' (非負整数) がありません (新形式 SQS スキーマを期待)"
            );
        }
        if (!node.isIntegralNumber()) {
            throw new ParseException(
                "SQS メッセージの 'batch_index' は整数である必要があります: " + node
            );
        }
        int value = node.asInt();
        if (value < 0) {
            throw new ParseException(
                "SQS メッセージの 'batch_index' は非負整数である必要があります: " + value
            );
        }
        return value;
    }

    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
