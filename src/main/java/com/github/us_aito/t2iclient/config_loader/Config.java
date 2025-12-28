package com.github.us_aito.t2iclient.config_loader;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Config(
  @JsonProperty("comfyui_config") ComfyuiConfig comfyuiConfig,
  @JsonProperty("workflow_config") WorkflowConfig workflowConfig,
  List<Scene> scenes
) {}
