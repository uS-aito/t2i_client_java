package com.github.us_aito.t2iclient.workflow_loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.us_aito.t2iclient.library_loader.LibraryLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkflowLoader {
  public static ObjectNode loadWorkflow(String workflowFilePath) throws IOException, JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return (ObjectNode) mapper.readTree(
      Files.readString(Path.of(workflowFilePath))
    );
  }

  public static void main(String[] args) {
    try {
      Map<String, List<String>> library = LibraryLoader.loadLibrary("./src/main/resources/sample_library.yaml");

      library.forEach((key, value) -> {
        System.out.println("Key: " + key);
        System.out.println("Values: " + value);
      });

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
