package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkflowConfig(
  @JsonProperty("workflow_json_path") String workflowJsonPath,
  @JsonProperty("image_output_path") String imageOutputPath,
  @JsonProperty("library_file_path") String libraryFilePath,
  @JsonProperty("default_prompts") DefaultPrompts defaultPrompts
) {}
