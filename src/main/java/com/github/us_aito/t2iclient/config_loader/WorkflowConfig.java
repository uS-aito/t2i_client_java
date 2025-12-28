package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkflowConfig(
  @JsonProperty("workflow_json_path") String workflowJsonPath,
  @JsonProperty("image_output_path") String imageOutputPath,
  @JsonProperty("library_file_path") String libraryFilePath,
  @JsonProperty("seed_node_id") Integer seedNodeId,
  @JsonProperty("batch_size_node_id") Integer batchSizeNodeId,
  @JsonProperty("negative_prompt_node_id") Integer negativePromptNodeId,
  @JsonProperty("positive_prompt_node_id") Integer positivePromptNodeId,
  @JsonProperty("environment_prompt_node_id") Integer environmentPromptNodeId,
  @JsonProperty("default_prompts") DefaultPrompts defaultPrompts
) {}
