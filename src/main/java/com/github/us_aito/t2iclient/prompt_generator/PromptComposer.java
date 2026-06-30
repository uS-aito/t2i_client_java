package com.github.us_aito.t2iclient.prompt_generator;

public final class PromptComposer {

    private PromptComposer() {}

    public static String composeNegative(String base, String body) {
        boolean hasBase = base != null && !base.trim().isEmpty();
        boolean hasBody = body != null && !body.trim().isEmpty();
        if (hasBase && hasBody) return base.trim() + ", " + body.trim();
        if (hasBase) return base.trim();
        if (hasBody) return body.trim();
        return "";
    }
}
