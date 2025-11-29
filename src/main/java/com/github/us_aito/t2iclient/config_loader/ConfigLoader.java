package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
  public static Config loadConfig(String configPath) throws IOException {
    String yamlContent = Files.readString(Path.of(configPath));

    var mapper = new ObjectMapper(new YAMLFactory());
    Config config = mapper.readValue(yamlContent, Config.class);

    return config;
  }

  public static void main(String[] args) {
    try {
      String testConfigPath = "./src/main/resources/sample_config.yaml";
      Config config = loadConfig(testConfigPath);

      System.out.println("ComfyUI Server Address: " + config.comfyui_config().server_address());
      System.out.println("Workflow JSON Path: " + config.workflow_config().workflow_json_path());
      config.scenes().forEach(scene -> {
        System.out.println("Scene Name: " + scene.name());
        System.out.println("Positive Prompt: " + scene.positive_prompt());
        System.out.println("Negative Prompt: " + scene.negative_prompt());
      });

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
