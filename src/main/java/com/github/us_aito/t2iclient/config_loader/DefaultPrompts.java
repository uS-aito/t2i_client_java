package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DefaultPrompts(
  @JsonProperty("base_positive_prompt") String basePositivePrompt,
  @JsonProperty("environment_prompt") String environmentPrompt,
  @JsonProperty("positive_prompt") String positivePrompt,
  @JsonProperty("negative_prompt") String negativePrompt,
  @JsonProperty("batch_size") Integer batchSize
) {}
