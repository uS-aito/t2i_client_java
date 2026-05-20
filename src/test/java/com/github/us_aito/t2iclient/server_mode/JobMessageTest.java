package com.github.us_aito.t2iclient.server_mode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobMessageTest {

    private static final String VALID_BODY = """
        {
          "project_name": "demo_project",
          "scene_name": "scene1",
          "serial": "20260520-143022",
          "batch_index": 0,
          "comfyui_payload": {
            "client_id": "my-client",
            "prompt": {"3": {"class_type": "KSampler"}}
          }
        }
        """.strip();

    @Test
    void parse_validNewFormat_returnsJobMessageWithMetadata() {
        JobMessage msg = JobMessage.parse("msg-001", "receipt-001", VALID_BODY);

        assertEquals("msg-001", msg.messageId());
        assertEquals("receipt-001", msg.receiptHandle());
        assertEquals("demo_project", msg.projectName());
        assertEquals("scene1", msg.sceneName());
        assertEquals("20260520-143022", msg.serial());
        assertEquals(0, msg.batchIndex());
        assertEquals("my-client", msg.clientId());
    }

    @Test
    void parse_validNewFormat_bodyHoldsInnerComfyuiPayloadJson() throws Exception {
        JobMessage msg = JobMessage.parse("msg-002", "receipt-002", VALID_BODY);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode innerNode = mapper.readTree(msg.body());
        assertEquals("my-client", innerNode.path("client_id").asText());
        assertTrue(innerNode.path("prompt").isObject());
        assertEquals("KSampler", innerNode.path("prompt").path("3").path("class_type").asText());
    }

    @Test
    void parse_batchIndexPositive_isParsedAsInt() {
        String body = VALID_BODY.replace("\"batch_index\": 0", "\"batch_index\": 7");

        JobMessage msg = JobMessage.parse("msg-003", "receipt-003", body);

        assertEquals(7, msg.batchIndex());
    }

    @Test
    void parse_missingProjectName_throwsParseException() {
        String body = """
            {
              "scene_name": "scene1",
              "serial": "20260520-143022",
              "batch_index": 0,
              "comfyui_payload": {"client_id": "c", "prompt": {"1": {}}}
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_missingSceneName_throwsParseException() {
        String body = """
            {
              "project_name": "p",
              "serial": "20260520-143022",
              "batch_index": 0,
              "comfyui_payload": {"client_id": "c", "prompt": {"1": {}}}
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_missingSerial_throwsParseException() {
        String body = """
            {
              "project_name": "p",
              "scene_name": "s",
              "batch_index": 0,
              "comfyui_payload": {"client_id": "c", "prompt": {"1": {}}}
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_missingBatchIndex_throwsParseException() {
        String body = """
            {
              "project_name": "p",
              "scene_name": "s",
              "serial": "20260520-143022",
              "comfyui_payload": {"client_id": "c", "prompt": {"1": {}}}
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_missingComfyuiPayload_throwsParseException() {
        String body = """
            {
              "project_name": "p",
              "scene_name": "s",
              "serial": "20260520-143022",
              "batch_index": 0
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_missingPromptInsideComfyuiPayload_throwsParseException() {
        String body = """
            {
              "project_name": "p",
              "scene_name": "s",
              "serial": "20260520-143022",
              "batch_index": 0,
              "comfyui_payload": {"client_id": "c"}
            }
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_batchIndexAsString_throwsParseException() {
        String body = VALID_BODY.replace("\"batch_index\": 0", "\"batch_index\": \"0\"");

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_batchIndexNegative_throwsParseException() {
        String body = VALID_BODY.replace("\"batch_index\": 0", "\"batch_index\": -1");

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_oldFormatWithTopLevelClientIdAndPrompt_throwsParseException() {
        String body = """
            {"client_id":"old-client","prompt":{"3":{"class_type":"KSampler"}}}
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", body)
        );
    }

    @Test
    void parse_invalidJson_throwsParseException() {
        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("m", "r", "not-json")
        );
    }

    @Test
    void parse_missingClientIdInsideComfyuiPayload_returnsNullClientId() {
        String body = """
            {
              "project_name": "p",
              "scene_name": "s",
              "serial": "20260520-143022",
              "batch_index": 0,
              "comfyui_payload": {"prompt": {"1": {}}}
            }
            """.strip();

        JobMessage msg = JobMessage.parse("m", "r", body);

        assertNull(msg.clientId());
    }
}
