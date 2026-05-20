package com.github.us_aito.t2iclient.client_mode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

public final class SqsJobMessageBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public String build(String projectName,
                      String sceneName,
                      String serial,
                      int batchIndex,
                      String innerComfyuiPayloadJson) throws IOException {
    JsonNode innerPayload = MAPPER.readTree(innerComfyuiPayloadJson);

    ObjectNode outer = MAPPER.createObjectNode();
    outer.put("project_name", projectName);
    outer.put("scene_name", sceneName);
    outer.put("serial", serial);
    outer.put("batch_index", batchIndex);
    outer.set("comfyui_payload", innerPayload);

    return MAPPER.writeValueAsString(outer);
  }
}
