package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DefaultPromptsSnapshot(
    @JsonProperty("basePositivePrompt") String basePositivePrompt,
    @JsonProperty("positivePrompt") String positivePrompt,
    @JsonProperty("negativePrompt") String negativePrompt,
    @JsonProperty("baseNegativePrompt") String baseNegativePrompt,
    @JsonProperty("environmentPrompt") String environmentPrompt
) {}
