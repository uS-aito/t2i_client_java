package com.github.us_aito.t2iclient.resume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResumeManagerFileIOTest {

    private final ResumeManager manager = new ResumeManager();

    // ----------------------------------------
    // 2.1 getResumePath
    // ----------------------------------------

    @ParameterizedTest
    @CsvSource({
        "/path/to/config.yaml,           /path/to/config.resume.json",
        "/home/user/my_config.yaml,      /home/user/my_config.resume.json",
        "/data/settings.yml,             /data/settings.resume.json",
        "/simple/config.yaml,            /simple/config.resume.json"
    })
    void getResumePath_returnsCorrectPath(String configPath, String expectedResumePath) {
        Path result = ResumeManager.getResumePath(configPath.trim());
        assertEquals(Path.of(expectedResumePath.trim()), result);
    }

    @Test
    void getResumePath_configInCurrentDirectory() {
        Path result = ResumeManager.getResumePath("config.yaml");
        assertEquals(Path.of("config.resume.json"), result);
    }

    // ----------------------------------------
    // 2.1 save
    // ----------------------------------------

    @Test
    void save_writesJsonFileToPath(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        ResumeState state = buildSampleState();

        manager.save(state, resumePath);

        assertTrue(Files.exists(resumePath));
        String content = Files.readString(resumePath);
        assertTrue(content.contains("\"version\""));
        assertTrue(content.contains("\"nextSceneIndex\""));
        assertTrue(content.contains("\"configPath\""));
    }

    @Test
    void save_canBeDeserializedBackToResumeState(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        ResumeState original = buildSampleState();

        manager.save(original, resumePath);
        Optional<ResumeState> loaded = manager.load(resumePath);

        assertTrue(loaded.isPresent());
        ResumeState result = loaded.get();
        assertEquals(original.version(), result.version());
        assertEquals(original.configPath(), result.configPath());
        assertEquals(original.savedAt(), result.savedAt());
        assertEquals(original.nextSceneIndex(), result.nextSceneIndex());
        assertEquals(original.scenes().size(), result.scenes().size());
        assertEquals(original.scenes().get(0).name(), result.scenes().get(0).name());
    }

    @Test
    void save_writesToStderrOnFailureWithoutThrowingException() {
        // 存在しない親ディレクトリにsaveしようとする
        Path invalidPath = Path.of("/nonexistent_dir_xyz/config.resume.json");
        ResumeState state = buildSampleState();

        // 例外がスローされないことを確認
        assertDoesNotThrow(() -> manager.save(state, invalidPath));
    }

    // ----------------------------------------
    // 2.2 load
    // ----------------------------------------

    @Test
    void load_returnsEmptyWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path resumePath = tempDir.resolve("nonexistent.resume.json");
        Optional<ResumeState> result = manager.load(resumePath);
        assertFalse(result.isPresent());
    }

    @Test
    void load_returnsEmptyAndDoesNotThrowForCorruptedJson(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("corrupt.resume.json");
        Files.writeString(resumePath, "{ this is not valid json ]]]");

        Optional<ResumeState> result = assertDoesNotThrow(() -> manager.load(resumePath));
        assertFalse(result.isPresent());
    }

    @Test
    void load_returnsResumeStateForValidJson(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildSampleState(), resumePath);

        Optional<ResumeState> result = manager.load(resumePath);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().version());
        assertEquals("/path/to/config.yaml", result.get().configPath());
        assertEquals(2, result.get().nextSceneIndex());
    }

    @Test
    void load_handlesNullableFieldsInSceneSnapshot(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        ResumeState stateWithNulls = new ResumeState(
            1,
            "/cfg.yaml",
            "2026-03-21T10:00:00",
            new DefaultPromptsSnapshot(null, null, null, null),
            List.of(new SceneSnapshot("scene1", null, "positive", null, null)),
            0
        );

        manager.save(stateWithNulls, resumePath);
        Optional<ResumeState> loaded = manager.load(resumePath);

        assertTrue(loaded.isPresent());
        assertNull(loaded.get().defaultPrompts().basePositivePrompt());
        assertNull(loaded.get().scenes().get(0).basePositivePrompt());
        assertEquals("positive", loaded.get().scenes().get(0).positivePrompt());
    }

    // ----------------------------------------
    // 2.3 delete
    // ----------------------------------------

    @Test
    void delete_removesFileWhenItExists(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        Files.writeString(resumePath, "{}");
        assertTrue(Files.exists(resumePath));

        manager.delete(resumePath);

        assertFalse(Files.exists(resumePath));
    }

    @Test
    void delete_afterDeleteLoadReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildSampleState(), resumePath);

        manager.delete(resumePath);
        Optional<ResumeState> result = manager.load(resumePath);

        assertFalse(result.isPresent());
    }

    @Test
    void delete_doesNotThrowWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path resumePath = tempDir.resolve("nonexistent.resume.json");
        assertDoesNotThrow(() -> manager.delete(resumePath));
    }

    // ----------------------------------------
    // helpers
    // ----------------------------------------

    private ResumeState buildSampleState() {
        DefaultPromptsSnapshot defaultPrompts = new DefaultPromptsSnapshot(
            "masterpiece, best quality",
            "positive_prompt",
            "blurry, low quality",
            "environment_prompt"
        );
        List<SceneSnapshot> scenes = List.of(
            new SceneSnapshot("sunset_beach", "A beautiful sunset", "sunset with <tree>", "blurry", "tropical"),
            new SceneSnapshot("mountain_lake", null, "A mountain lake", "overexposed", null)
        );
        return new ResumeState(1, "/path/to/config.yaml", "2026-03-21T10:30:15", defaultPrompts, scenes, 2);
    }
}
