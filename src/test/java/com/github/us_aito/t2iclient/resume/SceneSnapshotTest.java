package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 全フィールドを持つレコードをシリアライズ_デシリアライズできること() throws Exception {
        var snapshot = new SceneSnapshot(
            "sunset_beach",
            "A beautiful sunset at the beach",
            "sunset with <tree> trees",
            "blurry, low quality",
            "tropical, warm"
        );

        String json = mapper.writeValueAsString(snapshot);
        SceneSnapshot restored = mapper.readValue(json, SceneSnapshot.class);

        assertEquals(snapshot.name(), restored.name());
        assertEquals(snapshot.basePositivePrompt(), restored.basePositivePrompt());
        assertEquals(snapshot.positivePrompt(), restored.positivePrompt());
        assertEquals(snapshot.negativePrompt(), restored.negativePrompt());
        assertEquals(snapshot.environmentPrompt(), restored.environmentPrompt());
    }

    @Test
    void JSONフィールド名がキャメルケースで出力されること() throws Exception {
        var snapshot = new SceneSnapshot("scene1", "base", "pos", "neg", "env");

        String json = mapper.writeValueAsString(snapshot);

        assertTrue(json.contains("\"name\""), "name フィールドが必要");
        assertTrue(json.contains("\"basePositivePrompt\""), "basePositivePrompt フィールドが必要");
        assertTrue(json.contains("\"positivePrompt\""), "positivePrompt フィールドが必要");
        assertTrue(json.contains("\"negativePrompt\""), "negativePrompt フィールドが必要");
        assertTrue(json.contains("\"environmentPrompt\""), "environmentPrompt フィールドが必要");
    }

    @Test
    void nullフィールドがデシリアライズ後もnullであること() throws Exception {
        var snapshot = new SceneSnapshot("mountain_lake", null, "A serene lake with <weather>", "overexposed", null);

        String json = mapper.writeValueAsString(snapshot);
        SceneSnapshot restored = mapper.readValue(json, SceneSnapshot.class);

        assertEquals("mountain_lake", restored.name());
        assertNull(restored.basePositivePrompt());
        assertEquals("A serene lake with <weather>", restored.positivePrompt());
        assertNull(restored.environmentPrompt());
    }

    @Test
    void JSONから欠損フィールドをデシリアライズするとnullになること() throws Exception {
        String json = "{\"name\":\"forest_dawn\",\"positivePrompt\":\"a dark forest\"}";

        SceneSnapshot snapshot = mapper.readValue(json, SceneSnapshot.class);

        assertEquals("forest_dawn", snapshot.name());
        assertNull(snapshot.basePositivePrompt());
        assertEquals("a dark forest", snapshot.positivePrompt());
        assertNull(snapshot.negativePrompt());
        assertNull(snapshot.environmentPrompt());
    }

    @Test
    void Sceneレコードと同一のnullセマンティクスを持つこと() throws Exception {
        // Scene.java の nullable フィールドと同じ 4 フィールドを保持し batch_size は含まない
        var snapshot = new SceneSnapshot("test", null, null, null, null);

        String json = mapper.writeValueAsString(snapshot);

        assertFalse(json.contains("batchSize"), "batchSize は対象外");
        assertFalse(json.contains("batch_size"), "batch_size も対象外");
    }

    @Test
    void イミュータブルなレコードとして同値性が成立すること() {
        var a = new SceneSnapshot("beach", "base", "pos", "neg", "env");
        var b = new SceneSnapshot("beach", "base", "pos", "neg", "env");

        assertEquals(a, b);
    }
}
