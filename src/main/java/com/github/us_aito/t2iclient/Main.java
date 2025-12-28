package com.github.us_aito.t2iclient;

import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.IllegalArgumentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Iterator;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.us_aito.t2iclient.config_loader.Config;
import com.github.us_aito.t2iclient.config_loader.ConfigLoader;
import com.github.us_aito.t2iclient.library_loader.LibraryLoader;
import com.github.us_aito.t2iclient.prompt_generator.PromptGenerator;
import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;
import com.github.us_aito.t2iclient.workflow_loader.WorkflowLoader;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
public class Main {
  
  public static void main(String[] args) {
    log.info("Starting T2I Client...");

    String configPath = resolveConfigPath(args);
    WorkflowManager workflowManager = new WorkflowManager();
    ObjectMapper objectMapper = new ObjectMapper();
    CountDownLatch latch = new CountDownLatch(1);
    HttpClient client = HttpClient.newHttpClient();
    AtomicReference<String> promptId = new AtomicReference<>("");
    AtomicReference<String> sceneName = new AtomicReference<>("");
    AtomicInteger imageCount = new AtomicInteger(0);
    AtomicReference<String> webSocketDataCache = new AtomicReference<>("");
    AtomicReference<CompletableFuture<Void>> currentTask = new AtomicReference<>();

    try {
      Config config = ConfigLoader.loadConfig(configPath);
      Map<String, List<String>> library = LibraryLoader.loadLibrary(config.workflowConfig().libraryFilePath());
      ObjectNode workflow = WorkflowLoader.loadWorkflow(config.workflowConfig().workflowJsonPath());
      Random random = new Random();

      log.info("ComfyUI Server Address: {}", config.comfyuiConfig().serverAddress());
      log.info("Workflow JSON Path: {}", config.workflowConfig().workflowJsonPath());
      config.scenes().forEach(scene -> {
        log.info("Scene Name: {}", scene.name());
        log.info("Positive Prompt: {}", scene.positivePrompt());
        log.info("Negative Prompt: {}", scene.negativePrompt());
      });

      client.newWebSocketBuilder()
            .buildAsync(URI.create("ws://"+ config.comfyuiConfig().serverAddress() + "/ws?clientId=" + config.comfyuiConfig().clientId()), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    log.info("Connected to ComfyUI websocket server.");
                    WebSocket.Listener.super.onOpen(webSocket);
                }
                @Override
                // メッセージ受信
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    log.debug("Received data: {}", data);
                    webSocketDataCache.set(webSocketDataCache.get() + data.toString());

                    if (!last) {
                      log.debug("Still receiving data...");
                    } else {
                      JsonNode rootNode;
                      try {
                        rootNode = objectMapper.readTree(webSocketDataCache.get());
                      } catch (JsonProcessingException e) {
                        log.error("Unexpected Error:", e);
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                      }

                      String type = rootNode.path("type").asText("");
                      switch (type) {
                        case "executed":
                          log.info("Workflow executed.");
                          log.debug("Prompt ID: {}", promptId.get());
                          log.debug("Received Prompt ID: {}", rootNode.path("data").path("prompt_id").asText(""));
                          log.debug("Received Images: {}", rootNode.path("data").path("output").path("images").toString());

                          if (rootNode.path("data").path("prompt_id").asText("").equals(promptId.get())
                              && !(rootNode.path("data").path("output").path("images").isEmpty())) {
                            for (JsonNode image : rootNode.path("data").path("output").path("images")) {
                              Path savePath = Path.of(config.workflowConfig().imageOutputPath(), sceneName.get() + "_" + imageCount.get() + ".png");
                              try {
                                workflowManager.getImage(config.comfyuiConfig().serverAddress(), 
                                                        image.path("filename").asText(""),
                                                        savePath, 
                                                        image.path("type").asText(""),
                                                        image.path("subfolder").asText(""));
                              } catch (IOException | InterruptedException e) {
                                log.error("Unexpected Error:", e);
                              } finally {
                                log.info("Image saved to: {}", savePath.toString());
                              }
                            }
                          }
                          break;
                        case "execution_success":
                          log.info("Workflow execution succeeded.");
                          CompletableFuture<Void> f = currentTask.get();
                          if (f != null){
                            f.complete(null);
                          }
                          break;
                        case "executing":
                          log.info("Current Node is: {}", rootNode.path("data").path("node").asText());
                          break;
                        case "progress_state":
                          log.info("Progress State Report:");
                          JsonNode nodes = rootNode.path("data").path("nodes");
                          Iterator<String> fieldNames = nodes.fieldNames();
                          while (fieldNames.hasNext()) {
                            String nodeId = fieldNames.next();
                            JsonNode nodeInfo = nodes.path(nodeId);
                            log.info("  Node ID: {}, State: {}", nodeId, nodeInfo.path("state").asText());
                          }
                          break;
                        case "progress":
                          log.info("Progress Report:");
                          log.info("  Node ID: {} Progress: {}/{}", rootNode.path("data").path("node").asText(), rootNode.path("data").path("value").asInt(), rootNode.path("data").path("max").asInt());
                          break;
                        default:
                          log.info("未対応または不明なtype: {}", type);
                          log.info("message: {}", webSocketDataCache.get());
                          break;
                      }
                      webSocketDataCache.set(""); 
                    }

                    return WebSocket.Listener.super.onText(webSocket, data, last);
                }
                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    log.info("Disconnected from ComfyUI websocket server: " + reason);
                    latch.countDown();
                    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                }
                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    System.err.println("Error occurred on websocket: " + error.getMessage());
                    latch.countDown();
                }
            }).join();

      for (var scene : config.scenes()) {
        String basePositivePrompt = scene.basePositivePrompt() != null ? scene.basePositivePrompt() : config.workflowConfig().defaultPrompts().basePositivePrompt();
        String environmentPrompt = scene.environmentPrompt() != null ? scene.environmentPrompt() : config.workflowConfig().defaultPrompts().environmentPrompt();
        String positivePrompt = scene.positivePrompt() != null ? scene.positivePrompt() : config.workflowConfig().defaultPrompts().positivePrompt();
        String negativePrompt = scene.negativePrompt() != null ? scene.negativePrompt() : config.workflowConfig().defaultPrompts().negativePrompt();
        Integer batchSize = scene.batchSize() != null ? scene.batchSize() : config.workflowConfig().defaultPrompts().batchSize();
        sceneName.set(scene.name());

        List<String> generatedPrompts = PromptGenerator.generatePrompts(positivePrompt, library, batchSize);
        log.debug("Length of generated prompts: {}", generatedPrompts.size());
        imageCount.set(0);
        for (String prompt: generatedPrompts) {
          String fullPrompt = String.join(", ", basePositivePrompt, prompt);
          log.debug("Generated Prompt: {}", fullPrompt);
          workflow.withObject(config.workflowConfig().seedNodeId().toString()).withObject("inputs").put("seed",random.nextLong(0,1125899906842624L));
          workflow.withObject(config.workflowConfig().batchSizeNodeId().toString()).withObject("inputs").put("batch_size", 1);
          workflow.withObject(config.workflowConfig().negativePromptNodeId().toString()).withObject("inputs").put("text", negativePrompt);
          workflow.withObject(config.workflowConfig().positivePromptNodeId().toString()).withObject("inputs").put("wildcard_text", fullPrompt);
          workflow.withObject(config.workflowConfig().environmentPromptNodeId().toString()).withObject("inputs").put("text", environmentPrompt);
          log.debug("Sending prompt to ComfyUI: {}", fullPrompt);
          try {
            promptId.set(workflowManager.sendPrompt(
              config.comfyuiConfig().serverAddress(),
              config.comfyuiConfig().clientId(),
              objectMapper.convertValue(workflow, new TypeReference<Map<String, Object>>() {})
            ));
          } catch (IOException | InterruptedException e) {
            log.error("Unexpected Error:", e);
            return;
          }
          log.info("Received Prompt ID: {}", promptId.get());
          CompletableFuture<Void> future = new CompletableFuture<>();
          currentTask.set(new CompletableFuture<>());
          future.join();

          imageCount.getAndIncrement();
        }
      };

    } catch (IOException e) {
      log.error("Unexpected Error:", e);
      return;
    } catch (IllegalArgumentException e) {
      log.error("Unexpected Error", e.getMessage());
      return;
    } catch (Exception e) {
      log.error("Unexpected Error:", e);
      return;
    }
  }

  private static String resolveConfigPath(String[] args) {
    if (args.length > 0) {
      return args[0];
    }
    
    System.err.println("Usage: java -jar t2i_client.jar <path_to_config.yaml>");
    System.exit(1);

    return "";
  }
}
