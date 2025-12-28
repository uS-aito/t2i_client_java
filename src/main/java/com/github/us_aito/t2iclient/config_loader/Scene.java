package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Scene(
  String name,
  @JsonProperty("base_positive_prompt") String basePositivePrompt,
  @JsonProperty("positive_prompt") String positivePrompt,
  @JsonProperty("negative_prompt") String negativePrompt,
  @JsonProperty("environment_prompt") String environmentPrompt,
  @JsonProperty("batch_size") Integer batchSize
) {}
