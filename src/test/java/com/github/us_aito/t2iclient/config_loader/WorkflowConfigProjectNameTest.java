package com.github.us_aito.t2iclient.config_loader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

class WorkflowConfigProjectNameTest {

  @Test
  void projectNameIsDeserializedWhenPresent() throws Exception {
    String yaml = """
        workflow_json_path: /tmp/workflow.json
        image_output_path: /tmp/output
        library_file_path: /tmp/library.yaml
        seed_node_id: 1
        batch_size_node_id: 2
        negative_prompt_node_id: 3
        positive_prompt_node_id: 4
        environment_prompt_node_id: 5
        project_name: "demo_project"
        """;
    var mapper = new ObjectMapper(new YAMLFactory());

    WorkflowConfig config = mapper.readValue(yaml, WorkflowConfig.class);

    assertEquals("demo_project", config.projectName());
  }

  @Test
  void projectNameIsNullWhenAbsent() throws Exception {
    String yaml = """
        workflow_json_path: /tmp/workflow.json
        image_output_path: /tmp/output
        library_file_path: /tmp/library.yaml
        seed_node_id: 1
        batch_size_node_id: 2
        negative_prompt_node_id: 3
        positive_prompt_node_id: 4
        environment_prompt_node_id: 5
        """;
    var mapper = new ObjectMapper(new YAMLFactory());

    WorkflowConfig config = mapper.readValue(yaml, WorkflowConfig.class);

    assertNull(config.projectName());
  }

  @Test
  void configLoaderAcceptsLegacyConfigWithoutProjectName(@TempDir Path tmp) throws IOException {
    Path configPath = tmp.resolve("legacy_config.yaml");
    Files.writeString(configPath, """
        comfyui_config:
          server_address: 127.0.0.1:8188
          client_id: legacy_client
        workflow_config:
          workflow_json_path: /tmp/workflow.json
          image_output_path: /tmp/output
          library_file_path: /tmp/library.yaml
          seed_node_id: 1
          batch_size_node_id: 2
          negative_prompt_node_id: 3
          positive_prompt_node_id: 4
          environment_prompt_node_id: 5
        scenes:
          - name: "scene1"
            positive_prompt: "a"
            negative_prompt: "b"
        """);

    Config config = assertDoesNotThrow(() -> ConfigLoader.loadConfig(configPath.toString()));

    assertNotNull(config.workflowConfig());
    assertNull(config.workflowConfig().projectName());
  }

  @Test
  void configLoaderReadsProjectNameWhenPresent(@TempDir Path tmp) throws IOException {
    Path configPath = tmp.resolve("new_config.yaml");
    Files.writeString(configPath, """
        comfyui_config:
          server_address: 127.0.0.1:8188
          client_id: new_client
        workflow_config:
          project_name: "my_project"
          workflow_json_path: /tmp/workflow.json
          image_output_path: /tmp/output
          library_file_path: /tmp/library.yaml
          seed_node_id: 1
          batch_size_node_id: 2
          negative_prompt_node_id: 3
          positive_prompt_node_id: 4
          environment_prompt_node_id: 5
        scenes:
          - name: "scene1"
            positive_prompt: "a"
            negative_prompt: "b"
        """);

    Config config = ConfigLoader.loadConfig(configPath.toString());

    assertEquals("my_project", config.workflowConfig().projectName());
  }
}
