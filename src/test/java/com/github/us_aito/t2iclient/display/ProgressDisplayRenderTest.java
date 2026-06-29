package com.github.us_aito.t2iclient.display;

import org.junit.jupiter.api.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shift_JIS 基準時のダッシュボード描画統合テスト。
 * render() の出力を間接的に、構成ヘルパーの組み合わせで検証する。
 */
class ProgressDisplayRenderTest {

    // --- Shift_JIS 基準: ダッシュボード全構成要素のブロック文字非含有確認 ---

    @Test
    void allProgressLines_shiftJis_containNoBlockChars() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));

        String overall = display.buildOverallProgressLine(3, 6);
        String scene = display.buildSceneProgressLine(2, 4, 20);
        String node = display.buildNodeProgressLine("node78", 35, 35);
        String prompt = display.buildPromptLine(2, 3, "masterpiece, best quality");
        String result = display.buildResultsLine("output_001.png", 158);
        String header = display.buildHeader();

        String combined = overall + scene + node + prompt + result + header;

        assertFalse(combined.contains("█"), "Shift_JIS 基準でダッシュボード全体にブロック文字 █ が含まれないこと");
        assertFalse(combined.contains("░"), "Shift_JIS 基準でダッシュボード全体にブロック文字 ░ が含まれないこと");
    }

    // --- Shift_JIS 基準: 各要素の内容（バー以外）が維持されること ---

    @Test
    void overallProgressLine_shiftJis_preservesPercentageAndCount() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildOverallProgressLine(3, 6);

        assertTrue(line.contains("50%"), "パーセンテージが維持されること");
        assertTrue(line.contains("(3 / 6 scenes)"), "シーン件数が維持されること");
    }

    @Test
    void sceneProgressLine_shiftJis_preservesPercentageAndCount() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildSceneProgressLine(2, 3, 20);

        assertTrue(line.contains("(2 / 3 images)"), "画像件数が維持されること");
    }

    @Test
    void nodeProgressLine_shiftJis_preservesNodeIdAndStep() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildNodeProgressLine("node78", 35, 35);

        assertTrue(line.contains("node78"), "ノードIDが維持されること");
        assertTrue(line.contains("step 35/35"), "ステップ数が維持されること");
    }

    @Test
    void resultLine_shiftJis_preservesFilenameAndTotal() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildResultsLine("output_001.png", 158);

        assertTrue(line.contains("output_001.png"), "ファイル名が維持されること");
        assertTrue(line.contains("(total: 158)"), "累計画像数が維持されること");
    }

    @Test
    void headerLine_shiftJis_preserved() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String header = display.buildHeader();

        assertTrue(header.contains("t2i_client Progress Dashboard"), "ダッシュボードヘッダーが維持されること");
    }

    // --- UTF-8 基準: 従来のブロック文字表示が維持されること（退行確認） ---

    @Test
    void allProgressLines_utf8_containBlockChars() {
        ProgressDisplay display = new ProgressDisplay(StandardCharsets.UTF_8);

        String overall = display.buildOverallProgressLine(6, 6);
        String scene = display.buildSceneProgressLine(4, 4, 20);
        String node = display.buildNodeProgressLine("node1", 20, 20);

        String combined = overall + scene + node;

        assertTrue(combined.contains("█"), "UTF-8 基準では従来のブロック文字 █ が使われること（退行なし）");
    }
}
