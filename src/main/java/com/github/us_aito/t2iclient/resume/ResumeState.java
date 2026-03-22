package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ResumeState(
    @JsonProperty("version") int version,
    @JsonProperty("configPath") String configPath,
    @JsonProperty("savedAt") String savedAt,
    @JsonProperty("defaultPrompts") DefaultPromptsSnapshot defaultPrompts,
    @JsonProperty("scenes") List<SceneSnapshot> scenes,
    @JsonProperty("nextSceneIndex") int nextSceneIndex
) {}
