package com.github.us_aito.t2iclient.resume;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    @Test
    void 整合性ありの結果を生成できること() {
        var result = new ValidationResult(true, List.of());

        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void 不一致ありの結果に警告メッセージを格納できること() {
        var warnings = List.of(
            "シーン名が変更されました: forest_dawn → forest_morning",
            "default_prompts.negativePrompt が変更されました"
        );
        var result = new ValidationResult(false, warnings);

        assertFalse(result.valid());
        assertEquals(2, result.warnings().size());
        assertEquals("シーン名が変更されました: forest_dawn → forest_morning", result.warnings().get(0));
    }

    @Test
    void 同値性が成立すること() {
        var a = new ValidationResult(true, List.of("warn1"));
        var b = new ValidationResult(true, List.of("warn1"));

        assertEquals(a, b);
    }

    @Test
    void validがfalseのときwarningsに不一致理由が入ること() {
        var result = new ValidationResult(false, List.of("シーン数が変更されました: 3 → 2"));

        assertFalse(result.valid());
        assertFalse(result.warnings().isEmpty());
    }
}
