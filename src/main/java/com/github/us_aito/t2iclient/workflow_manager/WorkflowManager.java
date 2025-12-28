package com.github.us_aito.t2iclient.workflow_manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

record Workflow(
  String client_id,
  Object prompt
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record PromptResponse(
  String prompt_id
) {}

public class WorkflowManager {
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // public enum ImageType {
  //   INPUT, OUTPUT, TEMP;

  //   @Override
  //   public String toString() {
  //       return name().toLowerCase();
  //   }
  // }
  
  public WorkflowManager() {
    this.httpClient = HttpClient.newHttpClient();
  }
  WorkflowManager(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public String sendPrompt(String serverAddress, String clientId, Map<String, Object> workflowData) throws IOException, InterruptedException {

    Workflow workflow = new Workflow(clientId, workflowData);
    String jsonBody = objectMapper.writeValueAsString(workflow);

    HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://" + serverAddress).resolve("/prompt"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
      .build();

    HttpResponse<PromptResponse> httpResponse = httpClient.send(httpRequest, responseInfo ->
      HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
        (String body) -> {
          try {
            return objectMapper.readValue(body, PromptResponse.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      )
    );
    if (httpResponse.statusCode() != 200) {
      throw new IOException("Failed to send prompt. Status code: " + httpResponse.statusCode());
    }
    return httpResponse.body().prompt_id();
  }

  public Path getImage(String serverAddress, String imageName, Path savePath, String imageType, String subFolder) throws IOException, InterruptedException {

    URI baseUri = URI.create("http://" + serverAddress);
    String queryString = String.format("filename=%s&type=%s&subfolder=%s",
            URLEncoder.encode(imageName, StandardCharsets.UTF_8),
            URLEncoder.encode(imageType, StandardCharsets.UTF_8), // imageTypeもエンコード推奨
            URLEncoder.encode(subFolder, StandardCharsets.UTF_8)
    );
    
    HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("/view?" + queryString))
      .GET()
      .build();

    HttpResponse<Path> httpResponse = httpClient.send(httpRequest, 
      HttpResponse.BodyHandlers.ofFile(savePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

    if (httpResponse.statusCode() != 200) {
      throw new IOException("Failed to download image. Status code: " + httpResponse.statusCode());
    }

    return httpResponse.body();
  }
}
