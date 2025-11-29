package com.github.us_aito.t2iclient.library_loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

public class LibraryLoader {
  public static Map<String, List<String>> loadLibrary(String libraryFilePath) throws IOException, JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(
      Files.readString(Path.of(libraryFilePath)),
      new TypeReference<Map<String, List<String>>>() {}
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