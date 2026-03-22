package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeStateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void シリアライズとデシリアライズの往復で値が保持されること() throws Exception {
        var defaultPrompts = new DefaultPromptsSnapshot(
            "masterpiece, best quality",
            "positive prompt",
            "blurry, low quality",
            "tropical"
        );
        var scene1 = new SceneSnapshot("sunset_beach", "A beautiful sunset", "sunset with <tree>", "blurry", "tropical");
        var scene2 = new SceneSnapshot("mountain_lake", null, "A serene lake with <weather>", "overexposed", null);
        var original = new ResumeState(1, "/path/to/config.yaml", "2026-03-21T10:30:15",
            defaultPrompts, List.of(scene1, scene2), 1);

        String json = mapper.writeValueAsString(original);
        ResumeState restored = mapper.readValue(json, ResumeState.class);

        assertEquals(original.version(), restored.version());
        assertEquals(original.configPath(), restored.configPath());
        assertEquals(original.savedAt(), restored.savedAt());
        assertEquals(original.nextSceneIndex(), restored.nextSceneIndex());
        assertEquals(2, restored.scenes().size());

        assertEquals("sunset_beach", restored.scenes().get(0).name());
        assertNull(restored.scenes().get(1).basePositivePrompt());
        assertNull(restored.scenes().get(1).environmentPrompt());

        assertEquals("masterpiece, best quality", restored.defaultPrompts().basePositivePrompt());
    }

    @Test
    void JSONフィールド名が設計仕様通りであること() throws Exception {
        var defaultPrompts = new DefaultPromptsSnapshot("base", "pos", "neg", "env");
        var state = new ResumeState(1, "/cfg.yaml", "2026-01-01T00:00:00",
            defaultPrompts, List.of(), 0);

        String json = mapper.writeValueAsString(state);

        assertTrue(json.contains("\"version\""));
        assertTrue(json.contains("\"configPath\""));
        assertTrue(json.contains("\"savedAt\""));
        assertTrue(json.contains("\"defaultPrompts\""));
        assertTrue(json.contains("\"scenes\""));
        assertTrue(json.contains("\"nextSceneIndex\""));
    }

    @Test
    void defaultPromptsのnullフィールドがシリアライズされること() throws Exception {
        var defaultPrompts = new DefaultPromptsSnapshot(null, null, null, null);
        var state = new ResumeState(1, "/cfg.yaml", "2026-01-01T00:00:00",
            defaultPrompts, List.of(), 0);

        String json = mapper.writeValueAsString(state);
        ResumeState restored = mapper.readValue(json, ResumeState.class);

        assertNull(restored.defaultPrompts().basePositivePrompt());
        assertNull(restored.defaultPrompts().positivePrompt());
        assertNull(restored.defaultPrompts().negativePrompt());
        assertNull(restored.defaultPrompts().environmentPrompt());
    }
}
