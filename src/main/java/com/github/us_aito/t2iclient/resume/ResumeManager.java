package com.github.us_aito.t2iclient.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.us_aito.t2iclient.config_loader.DefaultPrompts;
import com.github.us_aito.t2iclient.config_loader.Scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

public class ResumeManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * configPathをもとにresumeファイルのパスを決定する。
     * 例: /path/to/config.yaml → /path/to/config.resume.json
     */
    public static Path getResumePath(String configPath) {
        Path p = Path.of(configPath);
        String fileName = p.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        String resumeFileName = baseName + ".resume.json";
        Path parent = p.getParent();
        return parent != null ? parent.resolve(resumeFileName) : Path.of(resumeFileName);
    }

    /**
     * ResumeStateをJSONとしてresumePathに書き込む。
     * 失敗時はIOExceptionをスローせずにstderrへ出力する。
     */
    public void save(ResumeState state, Path resumePath) {
        try {
            MAPPER.writeValue(resumePath.toFile(), state);
        } catch (IOException e) {
            System.err.println("[RESUME] resumeファイルの書き込みに失敗しました: " + resumePath + " - " + e.getMessage());
        }
    }

    /**
     * resumePathのJSONを読み込みResumeStateを返す。
     * ファイルが存在しない場合はOptional.empty()を返す。
     * 破損・パースエラーの場合はstderrに警告を出力してOptional.empty()を返す。
     */
    public Optional<ResumeState> load(Path resumePath) {
        if (!Files.exists(resumePath)) {
            return Optional.empty();
        }
        try {
            ResumeState state = MAPPER.readValue(resumePath.toFile(), ResumeState.class);
            return Optional.of(state);
        } catch (IOException e) {
            System.err.println("[RESUME] resumeファイルの読み込みに失敗しました（破損している可能性があります）: "
                + resumePath + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * resumePathのファイルを削除する。
     * 失敗時はstderrに警告を出力するが例外はスローしない。
     */
    public void delete(Path resumePath) {
        try {
            Files.deleteIfExists(resumePath);
        } catch (IOException e) {
            System.err.println("[RESUME] resumeファイルの削除に失敗しました: " + resumePath + " - " + e.getMessage());
        }
    }

    /**
     * resumeStateに保存されたシーン定義・default_promptsと現在の設定を照合する。
     */
    public ValidationResult validate(ResumeState state, List<Scene> currentScenes, DefaultPrompts currentDefaultPrompts) {
        List<String> warnings = new ArrayList<>();

        // シーンリストの照合
        List<SceneSnapshot> savedScenes = state.scenes();
        if (savedScenes.size() != currentScenes.size()) {
            warnings.add("シーン数が変更されました: " + savedScenes.size() + " → " + currentScenes.size());
        }

        int minSize = Math.min(savedScenes.size(), currentScenes.size());
        for (int i = 0; i < minSize; i++) {
            SceneSnapshot saved = savedScenes.get(i);
            Scene current = currentScenes.get(i);
            if (!Objects.equals(saved.name(), current.name())) {
                warnings.add("シーン[" + i + "]の名前が変更されました: " + saved.name() + " → " + current.name());
            } else {
                // 名前が一致するシーンのプロンプト比較
                if (!Objects.equals(saved.basePositivePrompt(), current.basePositivePrompt())) {
                    warnings.add("シーン[" + saved.name() + "]のbase_positive_promptが変更されました");
                }
                if (!Objects.equals(saved.positivePrompt(), current.positivePrompt())) {
                    warnings.add("シーン[" + saved.name() + "]のpositive_promptが変更されました");
                }
                if (!Objects.equals(saved.negativePrompt(), current.negativePrompt())) {
                    warnings.add("シーン[" + saved.name() + "]のnegative_promptが変更されました");
                }
                if (!Objects.equals(saved.environmentPrompt(), current.environmentPrompt())) {
                    warnings.add("シーン[" + saved.name() + "]のenvironment_promptが変更されました");
                }
            }
        }

        // savedに余分なシーンがある場合
        for (int i = minSize; i < savedScenes.size(); i++) {
            warnings.add("シーン[" + savedScenes.get(i).name() + "]が削除されました");
        }
        // currentに新規シーンがある場合
        for (int i = minSize; i < currentScenes.size(); i++) {
            warnings.add("シーン[" + currentScenes.get(i).name() + "]が追加されました");
        }

        // default_prompts の照合
        DefaultPromptsSnapshot savedDp = state.defaultPrompts();
        if (savedDp != null && currentDefaultPrompts != null) {
            if (!Objects.equals(savedDp.basePositivePrompt(), currentDefaultPrompts.basePositivePrompt())) {
                warnings.add("default_prompts.base_positive_promptが変更されました");
            }
            if (!Objects.equals(savedDp.positivePrompt(), currentDefaultPrompts.positivePrompt())) {
                warnings.add("default_prompts.positive_promptが変更されました");
            }
            if (!Objects.equals(savedDp.negativePrompt(), currentDefaultPrompts.negativePrompt())) {
                warnings.add("default_prompts.negative_promptが変更されました");
            }
            if (!Objects.equals(savedDp.environmentPrompt(), currentDefaultPrompts.environmentPrompt())) {
                warnings.add("default_prompts.environment_promptが変更されました");
            }
        }

        return new ValidationResult(warnings.isEmpty(), warnings);
    }

    /**
     * resumeファイルの検出・検証・ユーザー確認を一括で行い、
     * シーンループの開始インデックスを返す。
     * このメソッドはdisplay.start()より前に呼び出すこと。
     */
    public int checkAndPromptResume(String configPath, List<Scene> scenes, DefaultPrompts defaultPrompts, Path resumePath) {
        Optional<ResumeState> stateOpt = load(resumePath);
        if (stateOpt.isEmpty()) {
            return 0;
        }

        ResumeState state = stateOpt.get();
        ValidationResult validation = validate(state, scenes, defaultPrompts);

        if (!validation.valid()) {
            System.out.println("[RESUME] config.yamlとresumeファイルの間に不一致が検出されました:");
            for (String warning : validation.warnings()) {
                System.out.println("  - " + warning);
            }
            System.out.println("最初から実行する場合は Enter、中止する場合は q を入力してください:");
            String input = readLine();
            if (input != null && input.trim().equalsIgnoreCase("q")) {
                System.exit(0);
            }
            delete(resumePath);
            return 0;
        }

        // 整合性OK: 再開確認
        String nextSceneName = (state.nextSceneIndex() < scenes.size())
            ? scenes.get(state.nextSceneIndex()).name()
            : "(不明)";
        System.out.println("[RESUME] 前回の中断が検出されました。");
        System.out.println("  中断日時: " + state.savedAt());
        System.out.println("  再開予定シーン: " + nextSceneName + " (インデックス: " + state.nextSceneIndex() + ")");
        System.out.println("再開しますか？ (y/n):");
        String input = readLine();
        if (input != null && input.trim().equalsIgnoreCase("y")) {
            return state.nextSceneIndex();
        }

        delete(resumePath);
        return 0;
    }

    protected String readLine() {
        java.io.Console console = System.console();
        if (console != null) {
            return console.readLine();
        }
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }
    }
}
