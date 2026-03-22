package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPromptsSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 全フィールドを持つレコードをシリアライズ_デシリアライズできること() throws Exception {
        var snapshot = new DefaultPromptsSnapshot(
            "masterpiece, best quality",
            "positive prompt text",
            "blurry, low quality",
            "tropical environment"
        );

        String json = mapper.writeValueAsString(snapshot);
        DefaultPromptsSnapshot restored = mapper.readValue(json, DefaultPromptsSnapshot.class);

        assertEquals(snapshot.basePositivePrompt(), restored.basePositivePrompt());
        assertEquals(snapshot.positivePrompt(), restored.positivePrompt());
        assertEquals(snapshot.negativePrompt(), restored.negativePrompt());
        assertEquals(snapshot.environmentPrompt(), restored.environmentPrompt());
    }

    @Test
    void JSONフィールド名がキャメルケースで出力されること() throws Exception {
        var snapshot = new DefaultPromptsSnapshot("base", "pos", "neg", "env");

        String json = mapper.writeValueAsString(snapshot);

        assertTrue(json.contains("\"basePositivePrompt\""), "basePositivePrompt フィールドが必要");
        assertTrue(json.contains("\"positivePrompt\""), "positivePrompt フィールドが必要");
        assertTrue(json.contains("\"negativePrompt\""), "negativePrompt フィールドが必要");
        assertTrue(json.contains("\"environmentPrompt\""), "environmentPrompt フィールドが必要");
    }

    @Test
    void batchSizeフィールドが含まれないこと() throws Exception {
        var snapshot = new DefaultPromptsSnapshot("base", "pos", "neg", "env");

        String json = mapper.writeValueAsString(snapshot);

        assertFalse(json.contains("batchSize"), "batchSize は対象外");
        assertFalse(json.contains("batch_size"), "batch_size も対象外");
    }

    @Test
    void nullフィールドがデシリアライズ後もnullであること() throws Exception {
        var snapshot = new DefaultPromptsSnapshot(null, null, null, null);

        String json = mapper.writeValueAsString(snapshot);
        DefaultPromptsSnapshot restored = mapper.readValue(json, DefaultPromptsSnapshot.class);

        assertNull(restored.basePositivePrompt());
        assertNull(restored.positivePrompt());
        assertNull(restored.negativePrompt());
        assertNull(restored.environmentPrompt());
    }

    @Test
    void JSONからデシリアライズする際に欠損フィールドはnullになること() throws Exception {
        String json = "{\"basePositivePrompt\":\"base\"}";

        DefaultPromptsSnapshot snapshot = mapper.readValue(json, DefaultPromptsSnapshot.class);

        assertEquals("base", snapshot.basePositivePrompt());
        assertNull(snapshot.positivePrompt());
        assertNull(snapshot.negativePrompt());
        assertNull(snapshot.environmentPrompt());
    }

    @Test
    void イミュータブルなレコードとして同値性が成立すること() {
        var a = new DefaultPromptsSnapshot("base", "pos", "neg", "env");
        var b = new DefaultPromptsSnapshot("base", "pos", "neg", "env");

        assertEquals(a, b);
    }
}
