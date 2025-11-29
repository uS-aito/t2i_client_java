package com.github.us_aito.t2iclient.config_loader;

public record WorkflowConfig(
  String workflow_json_path,
  String image_output_path,
  String library_file_path,
  DefaultPrompts default_prompts
) {}
