package com.github.us_aito.t2iclient.prompt_generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.us_aito.t2iclient.config_loader.DefaultPrompts;
import com.github.us_aito.t2iclient.config_loader.WorkflowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowPatcherTest {

    private ObjectMapper mapper;
    private ObjectNode workflow;
    private WorkflowConfig workflowConfig;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        workflow = mapper.createObjectNode();
        // 各ノードIDのスタブ構造を作成
        for (int id : new int[]{1, 2, 3, 4, 5}) {
            workflow.withObject(String.valueOf(id)).withObject("inputs");
        }
        workflowConfig = new WorkflowConfig(
            "workflow.json", "/output", "library.yaml",
            1,  // seedNodeId
            2,  // batchSizeNodeId
            3,  // negativePromptNodeId
            4,  // positivePromptNodeId
            5,  // environmentPromptNodeId
            new DefaultPrompts(null, null, null, null, null)
        );
    }

    @Test
    void testApplyScenePrompts_patchesSeedNode() {
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "full prompt", "neg", "env", 12345L);

        long seed = workflow.path("1").path("inputs").path("seed").asLong();
        assertEquals(12345L, seed);
    }

    @Test
    void testApplyScenePrompts_patchesBatchSizeTo1() {
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "full prompt", "neg", "env", 0L);

        int batchSize = workflow.path("2").path("inputs").path("batch_size").asInt();
        assertEquals(1, batchSize);
    }

    @Test
    void testApplyScenePrompts_patchesNegativePrompt() {
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "full prompt", "neg prompt", "env", 0L);

        String text = workflow.path("3").path("inputs").path("text").asText();
        assertEquals("neg prompt", text);
    }

    @Test
    void testApplyScenePrompts_patchesPositivePrompt() {
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "the full prompt", "neg", "env", 0L);

        String text = workflow.path("4").path("inputs").path("wildcard_text").asText();
        assertEquals("the full prompt", text);
    }

    @Test
    void testApplyScenePrompts_patchesEnvironmentPrompt() {
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "full prompt", "neg", "env prompt", 0L);

        String text = workflow.path("5").path("inputs").path("text").asText();
        assertEquals("env prompt", text);
    }

    @Test
    void testApplyScenePrompts_returnsVoidAndWorkflowIsModifiedInPlace() {
        assertNotNull(workflow);
        WorkflowPatcher.applyScenePrompts(workflow, workflowConfig, "p", "n", "e", 999L);
        assertEquals(999L, workflow.path("1").path("inputs").path("seed").asLong());
    }
}
