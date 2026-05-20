package com.github.us_aito.t2iclient.client_mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SqsJobMessageBuilderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String INNER_PAYLOAD_JSON =
      "{\"client_id\":\"cid-123\",\"prompt\":{\"3\":{\"class_type\":\"KSampler\"}}}";

  private final SqsJobMessageBuilder builder = new SqsJobMessageBuilder();

  @Test
  void buildContainsAllFiveRequiredTopLevelKeys() throws IOException {
    String json = builder.build("my_project", "scene_a", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    assertTrue(node.has("project_name"));
    assertTrue(node.has("scene_name"));
    assertTrue(node.has("serial"));
    assertTrue(node.has("batch_index"));
    assertTrue(node.has("comfyui_payload"));
  }

  @Test
  void metadataValuesAreEmbeddedAsIs() throws IOException {
    String json = builder.build("my_project", "scene_a", "20260520-143022", 2, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    assertEquals("my_project", node.get("project_name").asText());
    assertEquals("scene_a", node.get("scene_name").asText());
    assertEquals("20260520-143022", node.get("serial").asText());
  }

  @Test
  void batchIndexIsEmbeddedAsJsonInteger() throws IOException {
    String json = builder.build("p", "s", "20260520-143022", 7, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    JsonNode batchIndexNode = node.get("batch_index");
    assertNotNull(batchIndexNode);
    assertTrue(batchIndexNode.isInt(), "batch_index should be a JSON integer");
    assertEquals(7, batchIndexNode.intValue());
  }

  @Test
  void batchIndexZeroIsEmbeddedAsJsonInteger() throws IOException {
    String json = builder.build("p", "s", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    JsonNode batchIndexNode = node.get("batch_index");
    assertTrue(batchIndexNode.isInt());
    assertEquals(0, batchIndexNode.intValue());
  }

  @Test
  void comfyuiPayloadIsEmbeddedAsObjectNotString() throws IOException {
    String json = builder.build("p", "s", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    JsonNode payload = node.get("comfyui_payload");
    assertTrue(payload.isObject(), "comfyui_payload should be a JSON object, not a string");
  }

  @Test
  void comfyuiPayloadPreservesClientIdAndPrompt() throws IOException {
    String json = builder.build("p", "s", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    JsonNode node = MAPPER.readTree(json);
    JsonNode payload = node.get("comfyui_payload");
    assertEquals("cid-123", payload.get("client_id").asText());
    assertNotNull(payload.get("prompt"));
    assertTrue(payload.get("prompt").isObject());
    assertEquals("KSampler", payload.get("prompt").get("3").get("class_type").asText());
  }

  @Test
  void comfyuiPayloadIsSemanticallyEquivalentToInnerJson() throws IOException {
    String json = builder.build("p", "s", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    JsonNode outer = MAPPER.readTree(json);
    JsonNode innerExpected = MAPPER.readTree(INNER_PAYLOAD_JSON);
    assertEquals(innerExpected, outer.get("comfyui_payload"));
  }

  @Test
  void topLevelKeyOrderIsStable() throws IOException {
    String json = builder.build("my_project", "scene_a", "20260520-143022", 0, INNER_PAYLOAD_JSON);

    int posProject = json.indexOf("\"project_name\"");
    int posScene = json.indexOf("\"scene_name\"");
    int posSerial = json.indexOf("\"serial\"");
    int posBatch = json.indexOf("\"batch_index\"");
    int posPayload = json.indexOf("\"comfyui_payload\"");

    assertTrue(posProject >= 0 && posScene > posProject
            && posSerial > posScene && posBatch > posSerial && posPayload > posBatch,
        "Expected fixed key order: project_name, scene_name, serial, batch_index, comfyui_payload. Got: " + json);
  }

  @Test
  void invalidInnerJsonThrowsIOException() {
    String invalidJson = "{not-a-json}";
    assertThrows(IOException.class,
        () -> builder.build("p", "s", "20260520-143022", 0, invalidJson));
  }
}
