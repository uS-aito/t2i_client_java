package com.github.us_aito.t2iclient;

import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.IllegalArgumentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
  
  private static final String DEV_CONFIG_PATH = "./src/main/resources/sample_config.yaml";
  public static void main(String[] args) {
    log.info("Starting T2I Client...");

    String configPath = resolveConfigPath(args);
    WorkflowManager workflowManager = new WorkflowManager();
    ObjectMapper objectMapper = new ObjectMapper();
    CountDownLatch latch = new CountDownLatch(1);
    HttpClient client = HttpClient.newHttpClient();
    AtomicBoolean callingAPIFlag = new AtomicBoolean(false);
    AtomicReference<String> promptId = new AtomicReference<>("");
    AtomicReference<String> sceneName = new AtomicReference<>("");
    AtomicInteger imageCount = new AtomicInteger(0);
    AtomicReference<String> webSocketDataCache = new AtomicReference<>("");

    try {
      Config config = ConfigLoader.loadConfig(configPath);
      Map<String, List<String>> library = LibraryLoader.loadLibrary(config.workflow_config().library_file_path());
      ObjectNode workflow = WorkflowLoader.loadWorkflow(config.workflow_config().workflow_json_path());
      Random random = new Random();

      log.info("ComfyUI Server Address: {}",config.comfyui_config().server_address());
      log.info("Workflow JSON Path: {}", config.workflow_config().workflow_json_path());
      config.scenes().forEach(scene -> {
        log.info("Scene Name: {}", scene.name());
        log.info("Positive Prompt: {}", scene.positive_prompt());
        log.info("Negative Prompt: {}", scene.negative_prompt());
      });

      client.newWebSocketBuilder()
            .buildAsync(URI.create("ws://"+ config.comfyui_config().server_address() + "/ws?clientId=" + config.comfyui_config().client_id()), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    log.info("Connected to ComfyUI websocket server.");
                    WebSocket.Listener.super.onOpen(webSocket);
                }
                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    // ★ここに受信時のビジネスロジックを書きます
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
                              Path savePath = Path.of(config.workflow_config().image_output_path(), sceneName.get() + "_" + imageCount.get() + ".png");
                              try {
                                workflowManager.getImage(config.comfyui_config().server_address(), 
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
                          callingAPIFlag.set(false);
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
                          // 知らないフォーマットや興味のないtypeは無視する
                          log.info("未対応または不明なtype: {}", type);
                          log.info("message: {}", webSocketDataCache.get());
                          break;
                      }
                      webSocketDataCache.set(""); 
                    }

                    // ★重要: これを返すと「処理完了、次のメッセージ受付OK」の合図になります
                    // 複雑なことをしない限り、この定型文でOKです
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
        String basePositivePrompt = scene.base_positive_prompt() != null ? scene.base_positive_prompt() : config.workflow_config().default_prompts().base_positive_prompt();
        String environmentPrompt = scene.environment_prompt() != null ? scene.environment_prompt() : config.workflow_config().default_prompts().environment_prompt();
        String positivePrompt = scene.positive_prompt() != null ? scene.positive_prompt() : config.workflow_config().default_prompts().positive_prompt();
        String negativePrompt = scene.negative_prompt() != null ? scene.negative_prompt() : config.workflow_config().default_prompts().negative_prompt();
        Integer batchSize = scene.batch_size() != null ? scene.batch_size() : config.workflow_config().default_prompts().batch_size();
        sceneName.set(scene.name());

        List<String> generatedPrompts = PromptGenerator.generatePrompts(positivePrompt, library, batchSize);
        log.debug("Length of generated prompts: {}", generatedPrompts.size());
        imageCount.set(0);
        for (String prompt: generatedPrompts) {
          String fullPrompt = String.join(", ", basePositivePrompt, prompt);
          log.debug("Generated Prompt: {}", fullPrompt);
          workflow.withObject("164").withObject("inputs").put("seed",random.nextLong(0,1125899906842624L));
          workflow.withObject("22").withObject("inputs").put("batch_size", 1);
          workflow.withObject("174").withObject("inputs").put("text", negativePrompt);
          workflow.withObject("257").withObject("inputs").put("wildcard_text", fullPrompt);
          workflow.withObject("303").withObject("inputs").put("text", environmentPrompt);

          log.debug("Sending prompt to ComfyUI: {}", fullPrompt);
          try {
            promptId.set(workflowManager.sendPrompt(
              config.comfyui_config().server_address(),
              config.comfyui_config().client_id(),
              objectMapper.convertValue(workflow, new TypeReference<Map<String, Object>>() {})
            ));
          } catch (IOException | InterruptedException e) {
            log.error("Unexpected Error:", e);
            return;
          }
          log.info("Received Prompt ID: {}", promptId.get());
          callingAPIFlag.set(true);
          while (callingAPIFlag.get()) {
            Thread.sleep(1000);
          }
          imageCount.getAndIncrement();
        }
      };

    } catch (IOException e) {
      log.error("Unexpected Error:", e);
      return;
    } catch (InterruptedException e) {
      log.error("Unexpected Error:", e);
      return;
    } catch (IllegalArgumentException e) {
      log.error("Unexpected Error", e.getMessage());
      return;
    } catch (Exception e) {
      log.error("Unexpected Error:", e);
      return;
    }
    // config内のscenesの各シーンをforで回す
    // 一つのシーンについて、batch_size回数だけ以下の処理を回す
    // プロンプトのレンダリングを行う(libraryを参照する)
    // comfyuiにリクエストを送信する
    // イメージをダウンロードしてシーン名やバッチサイズをベースとするファイル名で保存する
  }

  private static String resolveConfigPath(String[] args) {
    if (args.length > 0) {
      log.info("Using config path from command line argument: {}", args[0]);
      return args[0];
    }
    
    File devConfigFile = new File(DEV_CONFIG_PATH);
    if (devConfigFile.exists()) {
      log.info("Using default dev config path: {}", DEV_CONFIG_PATH);
      return DEV_CONFIG_PATH;
    }

    return "";
  }
}
