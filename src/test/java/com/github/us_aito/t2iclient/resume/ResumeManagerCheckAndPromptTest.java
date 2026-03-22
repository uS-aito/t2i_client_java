package com.github.us_aito.t2iclient.resume;

import com.github.us_aito.t2iclient.config_loader.DefaultPrompts;
import com.github.us_aito.t2iclient.config_loader.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * タスク4: 起動時のresume確認フロー checkAndPromptResume() のテスト
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.5, 5.3
 */
class ResumeManagerCheckAndPromptTest {

    /** readLine() をオーバーライドして入力をシミュレートするテスト用サブクラス */
    private static class StubResumeManager extends ResumeManager {
        private final String[] inputs;
        private int index = 0;

        StubResumeManager(String... inputs) {
            this.inputs = inputs;
        }

        @Override
        protected String readLine() {
            return index < inputs.length ? inputs[index++] : null;
        }
    }

    // ----------------------------------------
    // テスト用データ構築ヘルパー
    // ----------------------------------------

    private List<Scene> buildMatchingScenes() {
        return List.of(
            new Scene("sunset_beach", "A beautiful sunset", "sunset with <tree>", "blurry", "tropical", 1),
            new Scene("mountain_lake", null, "A mountain lake", "overexposed", null, 1)
        );
    }

    private DefaultPrompts buildMatchingDefaultPrompts() {
        return new DefaultPrompts("masterpiece", "environment", "positive", "blurry", 1);
    }

    private ResumeState buildMatchingState(int nextSceneIndex) {
        DefaultPromptsSnapshot dp = new DefaultPromptsSnapshot("masterpiece", "positive", "blurry", "environment");
        List<SceneSnapshot> scenes = List.of(
            new SceneSnapshot("sunset_beach", "A beautiful sunset", "sunset with <tree>", "blurry", "tropical"),
            new SceneSnapshot("mountain_lake", null, "A mountain lake", "overexposed", null)
        );
        return new ResumeState(1, "/path/to/config.yaml", "2026-03-21T10:30:15", dp, scenes, nextSceneIndex);
    }

    // ----------------------------------------
    // 要件5.3: resumeファイルなし → 問い合わせなし、0を返す
    // ----------------------------------------

    @Test
    void checkAndPromptResume_returnsZeroWithoutPromptWhenNoResumeFile(@TempDir Path tempDir) {
        // readLine を呼ばないことを確認するため、入力なしのスタブを使用
        StubResumeManager manager = new StubResumeManager(/* 入力なし */);
        Path resumePath = tempDir.resolve("config.resume.json");

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
    }

    // ----------------------------------------
    // 要件2.2, 2.3: 整合性OK + y入力 → nextSceneIndexを返す
    // ----------------------------------------

    @Test
    void checkAndPromptResume_returnsNextSceneIndexOnYInput(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager("y");
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildMatchingState(1), resumePath);

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(1, result);
    }

    @Test
    void checkAndPromptResume_resumeFileRemainsAfterYInput(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager("y");
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildMatchingState(1), resumePath);

        manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        // y選択時はresumeファイルを削除しない
        assertTrue(Files.exists(resumePath));
    }

    // ----------------------------------------
    // 要件2.4: 整合性OK + n入力 → resumeファイル削除、0を返す
    // ----------------------------------------

    @Test
    void checkAndPromptResume_returnsZeroAndDeletesFileOnNInput(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager("n");
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildMatchingState(1), resumePath);

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
        assertFalse(Files.exists(resumePath));
    }

    @Test
    void checkAndPromptResume_returnsZeroAndDeletesFileOnUppercaseNInput(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager("N");
        Path resumePath = tempDir.resolve("config.resume.json");
        manager.save(buildMatchingState(1), resumePath);

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
        assertFalse(Files.exists(resumePath));
    }

    // ----------------------------------------
    // 要件3.5: 整合性NG + Enter入力 → 最初から実行（0）、resumeファイル削除
    // ----------------------------------------

    @Test
    void checkAndPromptResume_returnsZeroAndDeletesFileWhenInvalidAndEnterPressed(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager(""); // Enter = 最初から実行
        Path resumePath = tempDir.resolve("config.resume.json");
        // シーン名が異なる不整合state
        DefaultPromptsSnapshot dp = new DefaultPromptsSnapshot("masterpiece", "positive", "blurry", "environment");
        ResumeState mismatchState = new ResumeState(
            1, "/path/to/config.yaml", "2026-03-21T10:30:15",
            dp,
            List.of(new SceneSnapshot("different_scene", null, null, null, null)),
            0
        );
        manager.save(mismatchState, resumePath);

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
        assertFalse(Files.exists(resumePath));
    }

    @Test
    void checkAndPromptResume_returnsZeroWhenDefaultPromptsChangedAndEnterPressed(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager(""); // Enter = 最初から実行
        Path resumePath = tempDir.resolve("config.resume.json");
        // default_promptsが異なる不整合state
        DefaultPromptsSnapshot differentDp = new DefaultPromptsSnapshot("CHANGED", "positive", "blurry", "environment");
        List<SceneSnapshot> scenes = List.of(
            new SceneSnapshot("sunset_beach", "A beautiful sunset", "sunset with <tree>", "blurry", "tropical"),
            new SceneSnapshot("mountain_lake", null, "A mountain lake", "overexposed", null)
        );
        ResumeState mismatchState = new ResumeState(1, "/path/to/config.yaml", "2026-03-21T10:30:15", differentDp, scenes, 0);
        manager.save(mismatchState, resumePath);

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
        assertFalse(Files.exists(resumePath));
    }

    // ----------------------------------------
    // 要件3.6: resumeファイルが破損している場合 → 0を返す（問い合わせなし）
    // ----------------------------------------

    @Test
    void checkAndPromptResume_returnsZeroWhenResumeFileCorrupted(@TempDir Path tempDir) throws IOException {
        StubResumeManager manager = new StubResumeManager(/* 入力なし */);
        Path resumePath = tempDir.resolve("config.resume.json");
        Files.writeString(resumePath, "{ invalid json }}}");

        int result = manager.checkAndPromptResume(
            "/path/to/config.yaml", buildMatchingScenes(), buildMatchingDefaultPrompts(), resumePath);

        assertEquals(0, result);
    }
}
