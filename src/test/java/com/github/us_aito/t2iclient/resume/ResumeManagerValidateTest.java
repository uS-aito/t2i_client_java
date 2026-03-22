package com.github.us_aito.t2iclient.resume;

import com.github.us_aito.t2iclient.config_loader.DefaultPrompts;
import com.github.us_aito.t2iclient.config_loader.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResumeManager.validate() のユニットテスト
 * タスク 3.1: シーン定義の変更検知
 * タスク 3.2: default_prompts の変更検知
 */
class ResumeManagerValidateTest {

    private final ResumeManager manager = new ResumeManager();

    // ----------------------------------------
    // ヘルパー
    // ----------------------------------------

    private ResumeState buildState(List<SceneSnapshot> scenes, DefaultPromptsSnapshot defaultPrompts) {
        return new ResumeState(1, "/path/to/config.yaml", "2026-03-21T10:00:00",
                defaultPrompts, scenes, 0);
    }

    private SceneSnapshot snapshot(String name, String base, String positive, String negative, String env) {
        return new SceneSnapshot(name, base, positive, negative, env);
    }

    private Scene scene(String name, String base, String positive, String negative, String env) {
        return new Scene(name, base, positive, negative, env, null);
    }

    private DefaultPromptsSnapshot dpSnapshot(String base, String positive, String negative, String env) {
        return new DefaultPromptsSnapshot(base, positive, negative, env);
    }

    private DefaultPrompts dp(String base, String positive, String negative, String env) {
        return new DefaultPrompts(base, env, positive, negative, null);
    }

    // ----------------------------------------
    // 3.1: シーン定義の変更検知
    // ----------------------------------------

    @Test
    void validate_returnsValidWhenScenesMatch() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("sunset_beach", "base", "positive", "negative", "env"),
                snapshot("mountain_lake", null, "a lake", "overexposed", null)
        );
        List<Scene> currentScenes = List.of(
                scene("sunset_beach", "base", "positive", "negative", "env"),
                scene("mountain_lake", null, "a lake", "overexposed", null)
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void validate_detectsSceneCountDifference() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("scene_a", "base", "positive", "negative", "env"),
                snapshot("scene_b", null, "positive2", null, null)
        );
        List<Scene> currentScenes = List.of(
                scene("scene_a", "base", "positive", "negative", "env")
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("シーン数")));
    }

    @Test
    void validate_detectsDeletedScenesWhenCurrentIsSmaller() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("scene_a", "base", "pos", "neg", "env"),
                snapshot("extra_scene", null, "extra", null, null)
        );
        List<Scene> currentScenes = List.of(
                scene("scene_a", "base", "pos", "neg", "env")
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("extra_scene")));
    }

    @Test
    void validate_detectsAddedScenesWhenCurrentIsLarger() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("scene_a", "base", "pos", "neg", "env")
        );
        List<Scene> currentScenes = List.of(
                scene("scene_a", "base", "pos", "neg", "env"),
                scene("new_scene", null, "new positive", null, null)
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("new_scene")));
    }

    @Test
    void validate_detectsSceneNameChange() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("old_name", "base", "pos", "neg", "env")
        );
        List<Scene> currentScenes = List.of(
                scene("new_name", "base", "pos", "neg", "env")
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("old_name") && w.contains("new_name")));
    }

    @ParameterizedTest(name = "{0}が変更された場合に警告を出す")
    @MethodSource("scenePromptChangeProvider")
    void validate_detectsScenePromptChange(String fieldDescription, SceneSnapshot saved, Scene current, String expectedWarningFragment) {
        List<SceneSnapshot> savedScenes = List.of(saved);
        List<Scene> currentScenes = List.of(current);
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid(), fieldDescription + " が変更されたのに valid=true");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains(expectedWarningFragment)),
                "警告に '" + expectedWarningFragment + "' が含まれていない: " + result.warnings());
    }

    static Stream<Arguments> scenePromptChangeProvider() {
        return Stream.of(
                Arguments.of(
                        "base_positive_prompt",
                        new SceneSnapshot("scene1", "original_base", "pos", "neg", "env"),
                        new Scene("scene1", "changed_base", "pos", "neg", "env", null),
                        "base_positive_prompt"
                ),
                Arguments.of(
                        "positive_prompt",
                        new SceneSnapshot("scene1", "base", "original_pos", "neg", "env"),
                        new Scene("scene1", "base", "changed_pos", "neg", "env", null),
                        "positive_prompt"
                ),
                Arguments.of(
                        "negative_prompt",
                        new SceneSnapshot("scene1", "base", "pos", "original_neg", "env"),
                        new Scene("scene1", "base", "pos", "changed_neg", "env", null),
                        "negative_prompt"
                ),
                Arguments.of(
                        "environment_prompt",
                        new SceneSnapshot("scene1", "base", "pos", "neg", "original_env"),
                        new Scene("scene1", "base", "pos", "neg", "changed_env", null),
                        "environment_prompt"
                )
        );
    }

    @Test
    void validate_noWarningForScenePromptWhenNameDiffers() {
        // シーン名が不一致の場合、プロンプト比較は行わない（名前変更の警告のみ）
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("old_name", "base", "pos", "neg", "env")
        );
        List<Scene> currentScenes = List.of(
                scene("new_name", "different_base", "different_pos", "different_neg", "different_env")
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        // シーン名変更の警告はあるが、プロンプトの変更警告は含まない
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("old_name") && w.contains("new_name")));
        assertFalse(result.warnings().stream().anyMatch(w -> w.contains("base_positive_prompt")));
    }

    @Test
    void validate_handlesNullableFieldsInSceneSnapshot() {
        // null フィールドが一致する場合、警告なし
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("scene1", null, "pos", null, null)
        );
        List<Scene> currentScenes = List.of(
                scene("scene1", null, "pos", null, null)
        );
        ResumeState state = buildState(savedScenes, dpSnapshot(null, null, null, null));
        DefaultPrompts currentDp = dp(null, null, null, null);

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void validate_detectsNullToNonNullPromptChange() {
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("scene1", null, "pos", "neg", "env")
        );
        List<Scene> currentScenes = List.of(
                scene("scene1", "newly_added_base", "pos", "neg", "env")
        );
        ResumeState state = buildState(savedScenes, dpSnapshot("base", "pos", "neg", "env"));
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("base_positive_prompt")));
    }

    // ----------------------------------------
    // 3.2: default_prompts の変更検知
    // ----------------------------------------

    @ParameterizedTest(name = "default_prompts.{0}が変更された場合に警告を出す")
    @MethodSource("defaultPromptsChangeProvider")
    void validate_detectsDefaultPromptsChange(String fieldName,
                                               DefaultPromptsSnapshot savedDp,
                                               DefaultPrompts currentDp,
                                               String expectedWarningFragment) {
        List<SceneSnapshot> savedScenes = List.of(snapshot("scene1", "base", "pos", "neg", "env"));
        List<Scene> currentScenes = List.of(scene("scene1", "base", "pos", "neg", "env"));
        ResumeState state = buildState(savedScenes, savedDp);

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid(), fieldName + " が変更されたのに valid=true");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains(expectedWarningFragment)),
                "警告に '" + expectedWarningFragment + "' が含まれていない: " + result.warnings());
    }

    static Stream<Arguments> defaultPromptsChangeProvider() {
        return Stream.of(
                Arguments.of(
                        "base_positive_prompt",
                        new DefaultPromptsSnapshot("original_base", "pos", "neg", "env"),
                        new DefaultPrompts("changed_base", "env", "pos", "neg", null),
                        "base_positive_prompt"
                ),
                Arguments.of(
                        "positive_prompt",
                        new DefaultPromptsSnapshot("base", "original_pos", "neg", "env"),
                        new DefaultPrompts("base", "env", "changed_pos", "neg", null),
                        "positive_prompt"
                ),
                Arguments.of(
                        "negative_prompt",
                        new DefaultPromptsSnapshot("base", "pos", "original_neg", "env"),
                        new DefaultPrompts("base", "env", "pos", "changed_neg", null),
                        "negative_prompt"
                ),
                Arguments.of(
                        "environment_prompt",
                        new DefaultPromptsSnapshot("base", "pos", "neg", "original_env"),
                        new DefaultPrompts("base", "changed_env", "pos", "neg", null),
                        "environment_prompt"
                )
        );
    }

    @Test
    void validate_returnsValidWhenDefaultPromptsMatch() {
        List<SceneSnapshot> savedScenes = List.of(snapshot("scene1", "base", "pos", "neg", "env"));
        List<Scene> currentScenes = List.of(scene("scene1", "base", "pos", "neg", "env"));
        DefaultPromptsSnapshot savedDp = dpSnapshot("base_val", "pos_val", "neg_val", "env_val");
        DefaultPrompts currentDp = dp("base_val", "pos_val", "neg_val", "env_val");
        ResumeState state = buildState(savedScenes, savedDp);

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void validate_collectsAllWarningsInSingleResult() {
        // シーン名変更 + default_prompts 変更が同時に起きた場合、すべての警告を1つの ValidationResult に集約する
        List<SceneSnapshot> savedScenes = List.of(
                snapshot("old_scene_name", "base", "pos", "neg", "env")
        );
        List<Scene> currentScenes = List.of(
                scene("new_scene_name", "base", "pos", "neg", "env")
        );
        DefaultPromptsSnapshot savedDp = dpSnapshot("original_base", "pos", "neg", "env");
        DefaultPrompts currentDp = dp("changed_base", "pos", "neg", "env");
        ResumeState state = buildState(savedScenes, savedDp);

        ValidationResult result = manager.validate(state, currentScenes, currentDp);

        assertFalse(result.valid());
        // シーン名変更の警告
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("old_scene_name")));
        // default_prompts 変更の警告
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("base_positive_prompt")));
    }

    @Test
    void validate_handlesNullDefaultPrompts() {
        // savedDp または currentDp が null の場合、クラッシュしない
        List<SceneSnapshot> savedScenes = List.of(snapshot("scene1", "base", "pos", "neg", "env"));
        List<Scene> currentScenes = List.of(scene("scene1", "base", "pos", "neg", "env"));
        ResumeState state = buildState(savedScenes, null);
        DefaultPrompts currentDp = dp("base", "pos", "neg", "env");

        assertDoesNotThrow(() -> manager.validate(state, currentScenes, currentDp));
    }
}
