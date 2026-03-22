package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SceneSnapshot(
    @JsonProperty("name") String name,
    @JsonProperty("basePositivePrompt") String basePositivePrompt,
    @JsonProperty("positivePrompt") String positivePrompt,
    @JsonProperty("negativePrompt") String negativePrompt,
    @JsonProperty("environmentPrompt") String environmentPrompt
) {}
