package com.github.us_aito.t2iclient.prompt_generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptComposerTest {

    @Test
    void nullとnullは空文字を返すこと() {
        assertEquals("", PromptComposer.composeNegative(null, null));
    }

    @Test
    void 空文字と空文字は空文字を返すこと() {
        assertEquals("", PromptComposer.composeNegative("", ""));
    }

    @Test
    void スペースのみと空白のみは空文字を返すこと() {
        assertEquals("", PromptComposer.composeNegative("  ", "  "));
    }

    @Test
    void baseのみ非空のときbaseを返すこと() {
        assertEquals("base", PromptComposer.composeNegative("base", null));
    }

    @Test
    void bodyのみ非空のときbodyを返すこと() {
        assertEquals("body", PromptComposer.composeNegative(null, "body"));
    }

    @Test
    void 両方非空のときカンマ連結を返すこと() {
        assertEquals("base, body", PromptComposer.composeNegative("base", "body"));
    }

    @Test
    void 前後スペースがtrimされてカンマ連結されること() {
        assertEquals("base, body", PromptComposer.composeNegative(" base ", " body "));
    }

    @Test
    void baseのみ非空でbodyが空文字のときbaseを返すこと() {
        assertEquals("base", PromptComposer.composeNegative("base", ""));
    }

    @Test
    void baseが空文字でbodyのみ非空のときbodyを返すこと() {
        assertEquals("body", PromptComposer.composeNegative("", "body"));
    }
}
