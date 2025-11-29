package com.github.us_aito.t2iclient.config_loader;

import java.util.List;

public record Config(
  ComfyuiConfig comfyui_config,
  WorkflowConfig workflow_config,
  List<Scene> scenes
) {}
