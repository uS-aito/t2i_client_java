package com.github.us_aito.t2iclient.config_loader;

public record Scene(
  String name,
  String base_positive_prompt,
  String positive_prompt,
  String negative_prompt,
  String environment_prompt,
  Integer batch_size
) {}
