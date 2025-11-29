package com.github.us_aito.t2iclient;

import java.io.IOException;

import com.github.us_aito.t2iclient.config_loader.Config;
import com.github.us_aito.t2iclient.config_loader.ConfigLoader;
import com.github.us_aito.t2iclient.library_loader.LibraryLoader;
import com.github.us_aito.t2iclient.prompt_generator.PromptGenerator;
import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;

import java.util.List;
import java.util.Map;

public class Main {
  public static void main(String[] args) {
    System.out.println("Starting T2I Client...");

    String configPath = "./src/main/resources/sample_config.yaml";
    String libraryFilePath = "./src/main/resources/sample_library.yaml";
    WorkflowManager workflowManager = new WorkflowManager();

    try {
      Config config = ConfigLoader.loadConfig(configPath);
      Map<String, List<String>> library = LibraryLoader.loadLibrary(libraryFilePath);

      System.out.println("ComfyUI Server Address: " + config.comfyui_config().server_address());
      System.out.println("Workflow JSON Path: " + config.workflow_config().workflow_json_path());
      config.scenes().forEach(scene -> {
        System.out.println("Scene Name: " + scene.name());
        System.out.println("Positive Prompt: " + scene.positive_prompt());
        System.out.println("Negative Prompt: " + scene.negative_prompt());
      });

      config.scenes().forEach(scene -> {
        String basePositivePrompt = scene.base_positive_prompt() != null ? scene.base_positive_prompt() : config.workflow_config().default_prompts().base_positive_prompt();
        String environmentPrompt = scene.environment_prompt() != null ? scene.environment_prompt() : config.workflow_config().default_prompts().environment_prompt();
        String positivePrompt = scene.positive_prompt() != null ? scene.positive_prompt() : config.workflow_config().default_prompts().positive_prompt();
        String negativePrompt = scene.negative_prompt() != null ? scene.negative_prompt() : config.workflow_config().default_prompts().negative_prompt();
        Integer batchSize = scene.batch_size() != null ? scene.batch_size() : config.workflow_config().default_prompts().batch_size();

        List<String> generatedPrompts = PromptGenerator.generatePrompts(positivePrompt, library, batchSize);
        for (String prompt: generatedPrompts) {
          String fullPrompt = String.join(", ", basePositivePrompt, environmentPrompt, prompt);
        }
      });

    } catch (IOException e) {
      e.printStackTrace();
      return;
    } 
    // config内のscenesの各シーンをforで回す
    // 一つのシーンについて、batch_size回数だけ以下の処理を回す
    // プロンプトのレンダリングを行う(libraryを参照する)
    // comfyuiにリクエストを送信する
    // イメージをダウンロードしてシーン名やバッチサイズをベースとするファイル名で保存する
  }
}
