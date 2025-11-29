package com.github.us_aito.t2iclient.config_loader;

public record DefaultPrompts(
  String base_positive_prompt,
  String environment_prompt,
  String positive_prompt,
  String negative_prompt,
  Integer batch_size
) {}
