package com.github.us_aito.t2iclient.display;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProgressDisplay の状態管理ユニットテスト（タスク 5.1 対応）
 * タスク 1.1 の TDD: 状態フィールドと公開メソッドの骨格を検証する
 */
class ProgressDisplayTest {

    private ProgressDisplay display;

    @BeforeEach
    void setUp() {
        display = new ProgressDisplay();
    }

    // --- startScene() のリセット検証 ---

    @Test
    void startScene_resetsSceneCompletedImages() {
        // GIVEN: 画像保存済みの状態
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.startScene("scene1", 1, 5);
        display.onImageSaved("img1.png");
        display.onImageSaved("img2.png");
        assertEquals(2, display.sceneCompletedImages);

        // WHEN: 新シーン開始
        display.startScene("scene2", 2, 3);

        // THEN: シーン内画像数がリセットされる
        assertEquals(0, display.sceneCompletedImages);
    }

    @Test
    void startScene_resetsPromptNumber() {
        // GIVEN: プロンプト送信済みの状態
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.startScene("scene1", 1, 5);
        display.startPrompt(3, "some long prompt text here");
        assertEquals(3, display.promptNumber);

        // WHEN: 新シーン開始
        display.startScene("scene2", 2, 3);

        // THEN: プロンプト番号がリセットされる
        assertEquals(0, display.promptNumber);
    }

    @Test
    void startScene_updatesSceneFields() {
        display.start("config.yaml", "localhost:8188", "/output", 3);

        display.startScene("myScene", 2, 7);

        assertEquals("myScene", display.currentSceneName);
        assertEquals(2, display.sceneIndex);
        assertEquals(7, display.sceneBatchSize);
    }

    // --- onImageSaved() のカウント検証 ---

    @Test
    void onImageSaved_incrementsTotalImages() {
        display.start("config.yaml", "localhost:8188", "/output", 2);
        display.startScene("scene1", 1, 3);
        assertEquals(0, display.totalImages);

        display.onImageSaved("img1.png");
        assertEquals(1, display.totalImages);

        display.onImageSaved("img2.png");
        assertEquals(2, display.totalImages);
    }

    @Test
    void onImageSaved_incrementsSceneCompletedImages() {
        display.start("config.yaml", "localhost:8188", "/output", 2);
        display.startScene("scene1", 1, 3);
        assertEquals(0, display.sceneCompletedImages);

        display.onImageSaved("img1.png");
        assertEquals(1, display.sceneCompletedImages);
    }

    @Test
    void onImageSaved_updatesLastImageFilename() {
        display.start("config.yaml", "localhost:8188", "/output", 2);
        display.startScene("scene1", 1, 3);

        display.onImageSaved("result_001.png");

        assertEquals("result_001.png", display.lastImageFilename);
    }

    @Test
    void onImageSaved_totalImagesAccumulatesAcrossScenes() {
        display.start("config.yaml", "localhost:8188", "/output", 2);
        display.startScene("scene1", 1, 2);
        display.onImageSaved("img1.png");
        display.onImageSaved("img2.png");
        // 新シーン開始
        display.startScene("scene2", 2, 2);
        display.onImageSaved("img3.png");

        // sceneCompletedImages はリセットされるが totalImages は累計
        assertEquals(1, display.sceneCompletedImages);
        assertEquals(3, display.totalImages);
    }

    // --- onExecutionComplete() のリセット検証 ---

    @Test
    void onExecutionComplete_resetsNodeInfo() {
        display.start("config.yaml", "localhost:8188", "/output", 1);
        display.startScene("scene1", 1, 1);
        display.onNodeExecuting("node42");
        display.onNodeProgress(8, 20);
        assertEquals("node42", display.currentNodeId);
        assertEquals(8, display.nodeStepValue);
        assertEquals(20, display.nodeStepMax);

        display.onExecutionComplete();

        assertNull(display.currentNodeId);
        assertEquals(0, display.nodeStepValue);
        assertEquals(0, display.nodeStepMax);
    }

    // --- startPrompt() の 60 文字切り詰め検証 ---

    @Test
    void startPrompt_truncatesTextTo60Chars() {
        display.start("config.yaml", "localhost:8188", "/output", 1);
        display.startScene("scene1", 1, 1);

        String longText = "a".repeat(100);
        display.startPrompt(1, longText);

        assertEquals(60, display.promptText.length());
    }

    @Test
    void startPrompt_doesNotTruncateShortText() {
        display.start("config.yaml", "localhost:8188", "/output", 1);
        display.startScene("scene1", 1, 1);

        display.startPrompt(1, "short text");

        assertEquals("short text", display.promptText);
    }

    @Test
    void startPrompt_treatsNullAsEmptyString() {
        display.start("config.yaml", "localhost:8188", "/output", 1);
        display.startScene("scene1", 1, 1);

        display.startPrompt(1, null);

        assertEquals("", display.promptText);
    }

    @Test
    void startPrompt_updatesPromptNumber() {
        display.start("config.yaml", "localhost:8188", "/output", 1);
        display.startScene("scene1", 1, 5);

        display.startPrompt(3, "text");

        assertEquals(3, display.promptNumber);
    }

    // --- onSceneComplete() の全体進捗カウント検証 ---

    @Test
    void onSceneComplete_incrementsCompletedScenes() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        assertEquals(0, display.completedScenes);

        display.startScene("scene1", 1, 1);
        display.onSceneComplete();
        assertEquals(1, display.completedScenes);

        display.startScene("scene2", 2, 1);
        display.onSceneComplete();
        assertEquals(2, display.completedScenes);
    }

    // --- start() の静的情報セット検証 ---

    @Test
    void start_setsStaticFields() {
        display.start("myconfig.yaml", "192.168.1.100:8188", "/images", 5);

        assertEquals("myconfig.yaml", display.configFileName);
        assertEquals("192.168.1.100:8188", display.serverAddress);
        assertEquals("/images", display.outputPath);
        assertEquals(5, display.totalScenes);
        assertNotNull(display.startTime);

        display.stop();
    }

    // --- タスク 1.2: バックグラウンドスレッドとターミナル制御 ---

    @Test
    void stop_withoutStart_doesNotThrow() {
        // start() を呼ばずに stop() しても例外が発生しないこと
        assertDoesNotThrow(() -> display.stop());
    }

    @Test
    void start_setsRunningTrue() {
        display.start("config.yaml", "localhost:8188", "/output", 3);

        assertTrue(display.running);

        display.stop();
    }

    @Test
    void stop_afterStart_setsRunningFalse() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.stop();

        assertFalse(display.running);
    }

    @Test
    void stop_isIdempotent() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.stop();

        // 2回目の stop() が例外を投げないこと
        assertDoesNotThrow(() -> display.stop());
    }

    @Test
    void stop_shutsDownScheduler() throws InterruptedException {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.stop();

        assertTrue(display.scheduler.isShutdown());
    }

    @Test
    void start_launchesRefreshThread() throws InterruptedException {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        // 100ms 待って render() が少なくとも1回呼ばれていることを確認
        Thread.sleep(200);

        assertTrue(display.renderCallCount > 0);

        display.stop();
    }

    // --- タスク 2.1: formatDuration ヘルパーの検証 ---

    @Test
    void formatDuration_zero() {
        assertEquals("00:00:00", display.formatDuration(0));
    }

    @Test
    void formatDuration_seconds() {
        assertEquals("00:00:45", display.formatDuration(45));
    }

    @Test
    void formatDuration_minutes() {
        assertEquals("00:05:03", display.formatDuration(303));
    }

    @Test
    void formatDuration_hours() {
        assertEquals("01:01:01", display.formatDuration(3661));
    }

    @Test
    void formatDuration_largeHours() {
        assertEquals("10:00:00", display.formatDuration(36000));
    }

    // --- タスク 2.1: renderProgressBar ヘルパーの検証 ---

    @Test
    void renderProgressBar_empty() {
        String bar = display.renderProgressBar(0, 10, 20);
        assertEquals("░░░░░░░░░░░░░░░░░░░░", bar);
    }

    @Test
    void renderProgressBar_full() {
        String bar = display.renderProgressBar(10, 10, 20);
        assertEquals("████████████████████", bar);
    }

    @Test
    void renderProgressBar_half() {
        String bar = display.renderProgressBar(5, 10, 20);
        assertEquals("██████████░░░░░░░░░░", bar);
    }

    @Test
    void renderProgressBar_zeroTotal_returnsAllEmpty() {
        String bar = display.renderProgressBar(0, 0, 10);
        assertEquals("░░░░░░░░░░", bar);
    }

    @Test
    void renderProgressBar_40percent() {
        // 4 / 10 = 40% → 8 filled, 12 empty (width=20)
        String bar = display.renderProgressBar(4, 10, 20);
        assertEquals("████████░░░░░░░░░░░░", bar);
    }

    // --- タスク 2.1: calculateEta ヘルパーの検証 ---

    @Test
    void calculateEta_noImagesCompleted_returnsPlaceholder() {
        display.start("config.yaml", "localhost:8188", "/output", 5);
        display.startScene("scene1", 1, 3);
        // totalImages = 0, totalScenes = 5

        String eta = display.calculateEta();
        assertEquals("--:--:--", eta);
        display.stop();
    }

    // --- タスク 2.2: buildOverallProgressLine ヘルパーの検証 ---

    @Test
    void buildOverallProgressLine_zeroProgress() {
        // 0 / 5 シーン完了 → 0%, バーは全て空
        String line = display.buildOverallProgressLine(0, 5);
        assertTrue(line.contains("░░░░░░░░░░░░░░░░░░░░"), "バーが全て空のはず: " + line);
        assertTrue(line.contains("0%"), "0% を含むはず: " + line);
        assertTrue(line.contains("(0 / 5 scenes)"), "(0 / 5 scenes) を含むはず: " + line);
    }

    @Test
    void buildOverallProgressLine_fullProgress() {
        // 5 / 5 シーン完了 → 100%, バーは全て塗り
        String line = display.buildOverallProgressLine(5, 5);
        assertTrue(line.contains("████████████████████"), "バーが全て塗りのはず: " + line);
        assertTrue(line.contains("100%"), "100% を含むはず: " + line);
        assertTrue(line.contains("(5 / 5 scenes)"), "(5 / 5 scenes) を含むはず: " + line);
    }

    @Test
    void buildOverallProgressLine_halfProgress() {
        // 2 / 4 シーン完了 → 50%
        String line = display.buildOverallProgressLine(2, 4);
        assertTrue(line.contains("50%"), "50% を含むはず: " + line);
        assertTrue(line.contains("(2 / 4 scenes)"), "(2 / 4 scenes) を含むはず: " + line);
    }

    @Test
    void buildOverallProgressLine_zeroTotal() {
        // 合計 0 のエッジケース → 0%, バーは全て空
        String line = display.buildOverallProgressLine(0, 0);
        assertTrue(line.contains("0%"), "0% を含むはず: " + line);
        assertTrue(line.contains("(0 / 0 scenes)"), "(0 / 0 scenes) を含むはず: " + line);
    }

    @Test
    void calculateEta_withCompletedImages_returnsFormattedEta() {
        // start() ではなく直接フィールドをセットして時刻制御
        display.startTime = java.time.Instant.now().minusSeconds(60);
        display.totalImages = 1;
        display.totalScenes = 5;
        display.completedScenes = 0;
        // sceneBatchSize など残数計算は totalScenes * 仮バッチサイズ ではなく
        // remainingImages = totalExpected - totalImages
        // totalExpected は totalScenes * sceneBatchSize だが、ここでは
        // totalImages=1, remainingImages が必要なのでフィールドをセット
        display.sceneBatchSize = 2; // 各シーン2枚 → 合計10枚
        display.sceneCompletedImages = 1;

        // elapsed=60s, completed=1, total=10, remaining=9 → ETA = 60*9 = 540s
        String eta = display.calculateEta();
        assertNotEquals("--:--:--", eta);
        // 540秒 = 00:09:00
        assertEquals("00:09:00", eta);
    }

    // ============================================================
    // タスク 3.1: 現在シーンの進捗とプロンプト情報の描画
    // ============================================================

    // --- buildSceneProgressLine() の検証 ---

    @Test
    void buildSceneProgressLine_showsProgressBar() {
        // 3 / 6 完了 → 50%
        String line = display.buildSceneProgressLine(3, 6, 20);
        assertTrue(line.contains("██████████░░░░░░░░░░"), "50% バーを含むはず: " + line);
    }

    @Test
    void buildSceneProgressLine_showsImageCount() {
        String line = display.buildSceneProgressLine(2, 5, 20);
        assertTrue(line.contains("(2 / 5 images)"), "(2 / 5 images) を含むはず: " + line);
    }

    @Test
    void buildSceneProgressLine_showsPercentage() {
        // 0 / 4 → 0%
        String line = display.buildSceneProgressLine(0, 4, 20);
        assertTrue(line.contains("0%"), "0% を含むはず: " + line);
    }

    @Test
    void buildSceneProgressLine_fullProgress() {
        String line = display.buildSceneProgressLine(6, 6, 20);
        assertTrue(line.contains("████████████████████"), "100% バーを含むはず: " + line);
        assertTrue(line.contains("100%"), "100% を含むはず: " + line);
        assertTrue(line.contains("(6 / 6 images)"), "(6 / 6 images) を含むはず: " + line);
    }

    @Test
    void buildSceneProgressLine_zeroTotal() {
        // batchSize=0 のエッジケース
        String line = display.buildSceneProgressLine(0, 0, 20);
        assertTrue(line.contains("0%"), "0% を含むはず: " + line);
        assertTrue(line.contains("(0 / 0 images)"), "(0 / 0 images) を含むはず: " + line);
    }

    // --- buildPromptLine() の検証 ---

    @Test
    void buildPromptLine_showsPromptNumbers() {
        String line = display.buildPromptLine(2, 5, "a cat");
        assertTrue(line.contains("2 / 5"), "2 / 5 を含むはず: " + line);
    }

    @Test
    void buildPromptLine_showsPromptText() {
        String line = display.buildPromptLine(1, 3, "beautiful landscape");
        assertTrue(line.contains("beautiful landscape"), "プロンプトテキストを含むはず: " + line);
    }

    @Test
    void buildPromptLine_handlesNullPromptText() {
        // null の場合に例外が出ず、空文字相当が表示されること
        assertDoesNotThrow(() -> display.buildPromptLine(1, 3, null));
    }

    @Test
    void buildPromptLine_handlesEmptyPromptText() {
        String line = display.buildPromptLine(1, 3, "");
        assertTrue(line.contains("1 / 3"), "1 / 3 を含むはず: " + line);
    }

    // ============================================================
    // タスク 3.2: ComfyUI 内部の実行状況と生成結果の描画
    // ============================================================

    // --- buildNodeProgressLine() の検証 ---

    @Test
    void buildNodeProgressLine_showsNodeId() {
        String line = display.buildNodeProgressLine("node42", 5, 20);
        assertTrue(line.contains("node42"), "ノードIDを含むはず: " + line);
    }

    @Test
    void buildNodeProgressLine_showsProgressBar() {
        // 10 / 20 = 50%
        String line = display.buildNodeProgressLine("node1", 10, 20);
        assertTrue(line.contains("██████████░░░░░░░░░░"), "50% バーを含むはず: " + line);
    }

    @Test
    void buildNodeProgressLine_showsStepInfo() {
        String line = display.buildNodeProgressLine("node1", 8, 20);
        assertTrue(line.contains("step 8/20"), "step 8/20 を含むはず: " + line);
    }

    @Test
    void buildNodeProgressLine_handlesNullNodeId() {
        // nodeId が null の場合に例外が出ないこと
        assertDoesNotThrow(() -> display.buildNodeProgressLine(null, 0, 0));
    }

    @Test
    void buildNodeProgressLine_zeroMax() {
        // stepMax=0 のエッジケース
        String line = display.buildNodeProgressLine("node1", 0, 0);
        assertTrue(line.contains("step 0/0"), "step 0/0 を含むはず: " + line);
    }

    // --- buildResultsLine() の検証 ---

    @Test
    void buildResultsLine_showsLastFilename() {
        String line = display.buildResultsLine("result_001.png", 3);
        assertTrue(line.contains("result_001.png"), "ファイル名を含むはず: " + line);
    }

    @Test
    void buildResultsLine_showsTotalImages() {
        String line = display.buildResultsLine("result_001.png", 7);
        assertTrue(line.contains("7"), "累計画像数を含むはず: " + line);
    }

    @Test
    void buildResultsLine_handlesNullFilename() {
        // ファイル名が null の場合に例外が出ないこと
        assertDoesNotThrow(() -> display.buildResultsLine(null, 0));
    }

    // ============================================================
    // タスク 5: ProgressDisplay 再開モード表示
    // ============================================================

    @Test
    void setResuming_false_headerDoesNotContainResumeLabel() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.setResuming(false);

        StringBuilder sb = new StringBuilder();
        // render() の出力をキャプチャする代わりに、ヘッダービルドロジックを直接検証
        // resuming=false の場合、buildHeader() は [RESUME] を含まない
        String header = display.buildHeader();
        assertFalse(header.contains("[RESUME]"), "[RESUME] を含まないはず: " + header);

        display.stop();
    }

    @Test
    void setResuming_true_headerContainsResumeLabel() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.setResuming(true);

        String header = display.buildHeader();
        assertTrue(header.contains("[RESUME]"), "[RESUME] を含むはず: " + header);

        display.stop();
    }

    @Test
    void setResuming_defaultIsFalse() {
        // start() 前はデフォルト false → [RESUME] ラベルなし
        String header = display.buildHeader();
        assertFalse(header.contains("[RESUME]"), "デフォルトは [RESUME] なしのはず: " + header);
    }

    @Test
    void setResuming_canBeToggledAfterStart() {
        display.start("config.yaml", "localhost:8188", "/output", 3);
        display.setResuming(true);
        assertTrue(display.buildHeader().contains("[RESUME]"));

        display.setResuming(false);
        assertFalse(display.buildHeader().contains("[RESUME]"));

        display.stop();
    }
}
