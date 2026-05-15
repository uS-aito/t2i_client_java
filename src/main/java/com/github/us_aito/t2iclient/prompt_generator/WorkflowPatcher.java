package com.github.us_aito.t2iclient.prompt_generator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.us_aito.t2iclient.config_loader.WorkflowConfig;

public final class WorkflowPatcher {

    private WorkflowPatcher() {}

    /**
     * シーン × プロンプトのノード値をワークフロー JSON に適用する。
     * Main.main の既存パッチ処理（seed/batch_size/prompts）と同等。
     */
    public static void applyScenePrompts(
            ObjectNode workflow,
            WorkflowConfig config,
            String fullPrompt,
            String negativePrompt,
            String environmentPrompt,
            long seed) {
        workflow.withObject(config.seedNodeId().toString()).withObject("inputs").put("seed", seed);
        workflow.withObject(config.batchSizeNodeId().toString()).withObject("inputs").put("batch_size", 1);
        workflow.withObject(config.negativePromptNodeId().toString()).withObject("inputs").put("text", negativePrompt);
        workflow.withObject(config.positivePromptNodeId().toString()).withObject("inputs").put("wildcard_text", fullPrompt);
        workflow.withObject(config.environmentPromptNodeId().toString()).withObject("inputs").put("text", environmentPrompt);
    }
}
