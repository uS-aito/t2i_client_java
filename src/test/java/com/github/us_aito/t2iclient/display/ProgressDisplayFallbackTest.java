package com.github.us_aito.t2iclient.display;

import org.junit.jupiter.api.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProgressDisplayFallbackTest {

    // --- resolveBarGlyphs のテスト ---

    @Test
    void resolveBarGlyphs_utf8_returnsBlockGlyphs() {
        String[] glyphs = ProgressDisplay.resolveBarGlyphs(StandardCharsets.UTF_8);
        assertEquals("█", glyphs[0], "filled は █ であること");
        assertEquals("░", glyphs[1], "empty は ░ であること");
    }

    @Test
    void resolveBarGlyphs_shiftJis_returnsAsciiGlyphs() {
        String[] glyphs = ProgressDisplay.resolveBarGlyphs(Charset.forName("Shift_JIS"));
        assertEquals("#", glyphs[0], "filled は # であること");
        assertEquals(".", glyphs[1], "empty は . であること");
    }

    @Test
    void resolveBarGlyphs_null_returnsAsciiGlyphs() {
        String[] glyphs = ProgressDisplay.resolveBarGlyphs(null);
        assertEquals("#", glyphs[0], "null 時は # にフォールバックすること");
        assertEquals(".", glyphs[1], "null 時は . にフォールバックすること");
    }

    // --- Shift_JIS 注入時の renderProgressBar テスト ---

    @Test
    void renderProgressBar_shiftJis_empty() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String bar = display.renderProgressBar(0, 10, 20);
        assertEquals(".".repeat(20), bar);
    }

    @Test
    void renderProgressBar_shiftJis_full() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String bar = display.renderProgressBar(10, 10, 20);
        assertEquals("#".repeat(20), bar);
    }

    @Test
    void renderProgressBar_shiftJis_half() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String bar = display.renderProgressBar(5, 10, 20);
        assertEquals("#".repeat(10) + ".".repeat(10), bar);
    }

    @Test
    void renderProgressBar_shiftJis_noBlockChars() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String bar = display.renderProgressBar(4, 10, 20);
        assertFalse(bar.contains("█"), "ブロック文字 █ を含まないこと");
        assertFalse(bar.contains("░"), "ブロック文字 ░ を含まないこと");
    }

    // --- buildOverallProgressLine / buildSceneProgressLine / buildNodeProgressLine のフォールバック確認 ---

    @Test
    void buildOverallProgressLine_shiftJis_noBlockChars() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildOverallProgressLine(3, 6);
        assertFalse(line.contains("█"), "全体バーにブロック文字を含まないこと");
        assertFalse(line.contains("░"), "全体バーにブロック文字を含まないこと");
        assertTrue(line.contains("50%"), "パーセンテージは維持されること");
        assertTrue(line.contains("(3 / 6 scenes)"), "件数は維持されること");
    }

    @Test
    void buildSceneProgressLine_shiftJis_noBlockChars() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildSceneProgressLine(2, 4, 20);
        assertFalse(line.contains("█"), "シーンバーにブロック文字を含まないこと");
        assertFalse(line.contains("░"), "シーンバーにブロック文字を含まないこと");
        assertTrue(line.contains("50%"), "パーセンテージは維持されること");
    }

    @Test
    void buildNodeProgressLine_shiftJis_noBlockChars() {
        ProgressDisplay display = new ProgressDisplay(Charset.forName("Shift_JIS"));
        String line = display.buildNodeProgressLine("node42", 10, 20);
        assertFalse(line.contains("█"), "ノードバーにブロック文字を含まないこと");
        assertFalse(line.contains("░"), "ノードバーにブロック文字を含まないこと");
        assertTrue(line.contains("node42"), "ノードIDは維持されること");
        assertTrue(line.contains("step 10/20"), "ステップ数は維持されること");
    }
}
