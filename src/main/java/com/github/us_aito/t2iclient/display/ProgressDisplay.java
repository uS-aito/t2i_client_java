package com.github.us_aito.t2iclient.display;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ターミナル進捗ダッシュボードの表示・管理クラス。
 * すべてのメソッドはスレッドセーフに設計されている。
 */
public final class ProgressDisplay {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ProgressDisplay.class);

    // 静的情報（起動時に1回セット）
    volatile String configFileName;
    volatile String serverAddress;
    volatile String outputPath;

    // 全体進捗
    volatile int totalScenes;
    volatile int completedScenes;

    // 現在シーン
    volatile String currentSceneName;
    volatile int sceneIndex;        // 1-origin
    volatile int sceneBatchSize;
    volatile int sceneCompletedImages;

    // 現在プロンプト
    volatile int promptNumber;      // 1-origin within scene
    volatile String promptText;     // truncated to 60 chars

    // ComfyUI ステータス
    volatile String currentNodeId;
    volatile int nodeStepValue;
    volatile int nodeStepMax;

    // 結果
    volatile String lastImageFilename;
    volatile int totalImages;

    // タイミング
    Instant startTime;              // 不変（start()時にセット）

    // スレッド管理
    volatile boolean running = false;
    ScheduledExecutorService scheduler;
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    // テスト用: render() の呼び出し回数
    volatile int renderCallCount = 0;

    /**
     * ダッシュボードを起動し、バックグラウンドリフレッシュを開始する。
     * logback の STDOUT アペンダーを detach し、ターミナルを確保する。
     *
     * @param configFileName コンフィグファイル名
     * @param serverAddress  ComfyUI サーバーアドレス
     * @param outputPath     画像出力ディレクトリ
     * @param totalScenes    処理予定の総シーン数
     */
    public void start(String configFileName, String serverAddress,
                      String outputPath, int totalScenes) {
        this.configFileName = configFileName;
        this.serverAddress = serverAddress;
        this.outputPath = outputPath;
        this.totalScenes = totalScenes;
        this.startTime = Instant.now();

        // logback の STDOUT アペンダーを detach してターミナル出力と競合しないようにする
        detachStdoutAppender();

        // カーソル非表示 + 画面クリア
        System.out.print("\u001B[?25l");
        System.out.print("\u001B[2J");
        System.out.flush();

        // シャットダウンフック（シグナル受信時にも stop() を保証）
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "progress-display-shutdown"));
        }

        // バックグラウンドリフレッシュスレッド起動
        this.running = true;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "progress-display-refresh");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, e) ->
                log.error("progress-display-refresh スレッドで未捕捉例外が発生しました", e));
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::render, 0, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 新しいシーンの処理開始を通知する。
     * シーン進捗・プロンプト番号をリセットする。
     */
    public void startScene(String sceneName, int sceneIndex, int batchSize) {
        this.currentSceneName = sceneName;
        this.sceneIndex = sceneIndex;
        this.sceneBatchSize = batchSize;
        this.sceneCompletedImages = 0;
        this.promptNumber = 0;
    }

    /**
     * プロンプト送信開始を通知する。
     * promptText は 60 文字に切り詰める。null の場合は空文字を使用。
     */
    public void startPrompt(int promptNumber, String promptText) {
        this.promptNumber = promptNumber;
        String text = promptText != null ? promptText : "";
        this.promptText = text.length() > 60 ? text.substring(0, 60) : text;
    }

    /**
     * ComfyUI の "executing" メッセージ受信を通知する。
     */
    public void onNodeExecuting(String nodeId) {
        this.currentNodeId = nodeId;
    }

    /**
     * ComfyUI の "progress" メッセージ受信を通知する。
     */
    public void onNodeProgress(int value, int max) {
        this.nodeStepValue = value;
        this.nodeStepMax = max;
    }

    /**
     * 画像ファイルの保存完了を通知する。
     * シーン内進捗・累計画像数をインクリメントする。
     */
    public void onImageSaved(String filename) {
        this.lastImageFilename = filename;
        this.sceneCompletedImages++;
        this.totalImages++;
    }

    /**
     * ComfyUI の "execution_success" メッセージ受信を通知する。
     * ノード進捗表示をリセットする。
     */
    public void onExecutionComplete() {
        this.currentNodeId = null;
        this.nodeStepValue = 0;
        this.nodeStepMax = 0;
    }

    /**
     * 現在シーンの完了を通知する。
     * 全体進捗カウンタをインクリメントする。
     */
    public void onSceneComplete() {
        this.completedScenes++;
    }

    /**
     * ダッシュボードを停止し、ターミナルを復元する。
     * カーソルを表示状態に戻し、完了メッセージを出力する。
     * シャットダウンフックからも呼び出し可能（冪等性あり）。
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // カーソル復元 + 完了メッセージ
        System.out.print("\u001B[?25h");
        System.out.println("\n処理が完了しました。");
        System.out.flush();
    }

    /**
     * ダッシュボードを定期描画する（バックグラウンドスレッドから呼び出される）。
     * 例外が発生してもスレッドが停止しないよう、すべての例外をキャッチしてログに記録する。
     */
    void render() {
        renderCallCount++;
        try {
            StringBuilder sb = new StringBuilder();

            // カーソルをホームに移動（上書き描画）
            sb.append("\u001B[H");

            // ヘッダー
            sb.append("=== t2i_client Progress Dashboard ===\u001B[K\n");
            sb.append("--------------------------------------\u001B[K\n");

            // 静的情報
            sb.append(String.format("Config   : %s\u001B[K%n", nvl(configFileName)));
            sb.append(String.format("Server   : %s\u001B[K%n", nvl(serverAddress)));
            sb.append(String.format("Output   : %s\u001B[K%n", nvl(outputPath)));

            // 経過時間
            long elapsedSec = startTime != null
                    ? java.time.Duration.between(startTime, java.time.Instant.now()).getSeconds()
                    : 0;
            sb.append(String.format("Elapsed  : %s\u001B[K%n", formatDuration(elapsedSec)));

            // ETA
            sb.append(String.format("ETA      : %s\u001B[K%n", calculateEta()));

            // 全体プログレスバー
            sb.append(String.format("Overall  : %s\u001B[K%n", buildOverallProgressLine(completedScenes, totalScenes)));

            // 区切り線
            sb.append("--------------------------------------\u001B[K\n");

            // 現在シーン名
            sb.append(String.format("Scene    : %s\u001B[K%n", nvl(currentSceneName)));

            // シーン内プログレスバー
            sb.append(String.format("Progress : %s\u001B[K%n", buildSceneProgressLine(sceneCompletedImages, sceneBatchSize, 20)));

            // プロンプト情報
            sb.append(String.format("Prompt   : %s\u001B[K%n", buildPromptLine(promptNumber, sceneBatchSize, promptText)));

            // 区切り線
            sb.append("--------------------------------------\u001B[K\n");

            // ComfyUI 実行状況
            sb.append(String.format("Node     : %s\u001B[K%n", buildNodeProgressLine(currentNodeId, nodeStepValue, nodeStepMax)));

            // 生成結果
            sb.append(String.format("Result   : %s\u001B[K%n", buildResultsLine(lastImageFilename, totalImages)));

            System.out.print(sb);
            System.out.flush();
        } catch (Exception e) {
            log.error("render() で例外が発生しました。リフレッシュスレッドを継続します。", e);
        }
    }

    /**
     * 秒数を HH:MM:SS 形式の文字列に変換する。
     *
     * @param totalSeconds 変換する秒数（負の値は 0 として扱う）
     * @return "HH:MM:SS" 形式の文字列
     */
    String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * テキスト形式のプログレスバーを生成する。
     *
     * @param completed 完了数
     * @param total     総数
     * @param width     バーの幅（文字数）
     * @return プログレスバー文字列（例: "██████░░░░"）
     */
    String renderProgressBar(int completed, int total, int width) {
        int filled = (total > 0) ? (int) Math.round((double) completed / total * width) : 0;
        filled = Math.max(0, Math.min(filled, width));
        return "\u2588".repeat(filled) + "\u2591".repeat(width - filled);
    }

    /**
     * 現在の ETA（完了予測時刻までの残り時間）を計算する。
     * 完了画像数が 0 の場合は "--:--:--" を返す。
     *
     * @return "HH:MM:SS" 形式の ETA 文字列、または "--:--:--"
     */
    String calculateEta() {
        if (startTime == null || totalImages <= 0) {
            return "--:--:--";
        }
        long elapsedSec = java.time.Duration.between(startTime, java.time.Instant.now()).getSeconds();
        int totalExpected = totalScenes * sceneBatchSize;
        int remaining = totalExpected - totalImages;
        if (remaining <= 0) {
            return "00:00:00";
        }
        long etaSec = elapsedSec * remaining / totalImages;
        return formatDuration(etaSec);
    }

    /**
     * 全体プログレスバー行を生成する（幅20, パーセンテージ, シーン数付き）。
     *
     * @param completed 完了シーン数
     * @param total     総シーン数
     * @return 例: "████████████████████ 100% (5 / 5 scenes)"
     */
    String buildOverallProgressLine(int completed, int total) {
        String bar = renderProgressBar(completed, total, 20);
        int pct = (total > 0) ? (int) Math.round((double) completed / total * 100) : 0;
        return String.format("%s %3d%% (%d / %d scenes)", bar, pct, completed, total);
    }

    /**
     * シーン内プログレスバー行を生成する（幅20, パーセンテージ, 画像数付き）。
     *
     * @param completedImages 完了画像数
     * @param batchSize       バッチサイズ（総画像数）
     * @param width           バーの幅
     * @return 例: "██████████░░░░░░░░░░  50% (3 / 6 images)"
     */
    String buildSceneProgressLine(int completedImages, int batchSize, int width) {
        String bar = renderProgressBar(completedImages, batchSize, width);
        int pct = (batchSize > 0) ? (int) Math.round((double) completedImages / batchSize * 100) : 0;
        return String.format("%s %3d%% (%d / %d images)", bar, pct, completedImages, batchSize);
    }

    /**
     * プロンプト情報行を生成する。
     *
     * @param promptNumber プロンプト番号（1-origin）
     * @param batchSize    シーンのバッチサイズ（総プロンプト数）
     * @param promptText   プロンプトテキスト（切り詰め済み）
     * @return 例: "2 / 5  a cat sitting on a..."
     */
    String buildPromptLine(int promptNumber, int batchSize, String promptText) {
        return String.format("%d / %d  %s", promptNumber, batchSize, nvl(promptText));
    }

    /**
     * ノード内ステップ進捗バー行を生成する。
     *
     * @param nodeId       実行中ノードID
     * @param stepValue    現在ステップ数
     * @param stepMax      最大ステップ数
     * @return 例: "node42  ██████████░░░░░░░░░░  step 10/20"
     */
    String buildNodeProgressLine(String nodeId, int stepValue, int stepMax) {
        String bar = renderProgressBar(stepValue, stepMax, 20);
        return String.format("%s  %s  step %d/%d", nvl(nodeId), bar, stepValue, stepMax);
    }

    /**
     * 生成結果行を生成する（最後の画像ファイル名と累計画像数）。
     *
     * @param lastFilename 最後に保存した画像ファイル名
     * @param totalImages  累計生成画像数
     * @return 例: "result_001.png  (total: 7)"
     */
    String buildResultsLine(String lastFilename, int totalImages) {
        return String.format("%s  (total: %d)", nvl(lastFilename), totalImages);
    }

    /** null を空文字に変換するユーティリティ。 */
    private static String nvl(String s) {
        return s != null ? s : "";
    }

    /**
     * logback の root ロガーから STDOUT アペンダーを detach する。
     * ダッシュボード表示とログ出力が混在しないようにする。
     */
    private void detachStdoutAppender() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            rootLogger.detachAppender("STDOUT");
        } catch (Exception e) {
            log.warn("logback の STDOUT アペンダーの detach に失敗しました。表示が混在する可能性があります。", e);
        }
    }
}
