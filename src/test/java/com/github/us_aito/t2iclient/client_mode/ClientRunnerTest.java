package com.github.us_aito.t2iclient.client_mode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ClientRunnerTest {

    private static final String QUEUE_URL = "https://sqs.ap-northeast-1.amazonaws.com/123456789012/test-queue";

    @Mock
    private SqsPromptPublisher mockPublisher;

    @TempDir
    Path tempDir;

    private Path configPath;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // 最小限のワークフローJSON
        Path workflowFile = tempDir.resolve("workflow.json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode workflow = mapper.createObjectNode();
        for (int id : new int[]{1, 2, 3, 4, 5}) {
            workflow.withObject(String.valueOf(id)).withObject("inputs");
        }
        Files.writeString(workflowFile, mapper.writeValueAsString(workflow));

        // 最小限のライブラリYAML
        Path libraryFile = tempDir.resolve("library.yaml");
        Files.writeString(libraryFile, "pose:\n  - standing\n  - sitting\n");

        // テスト用config YAML（scene1: batch_size=1）
        configPath = tempDir.resolve("config.yaml");
        String configYaml = String.format("""
            comfyui_config:
              server_address: localhost:8188
              client_id: test-client
            workflow_config:
              workflow_json_path: %s
              image_output_path: /tmp/output
              library_file_path: %s
              seed_node_id: 1
              batch_size_node_id: 2
              negative_prompt_node_id: 3
              positive_prompt_node_id: 4
              environment_prompt_node_id: 5
              default_prompts:
                base_positive_prompt: "base"
                environment_prompt: "env"
                positive_prompt: "a <pose> scene"
                negative_prompt: "bad"
                batch_size: 2
            scenes:
              - name: "scene1"
                positive_prompt: "a <pose> person"
                batch_size: 1
              - name: "scene2"
                positive_prompt: "a <pose> place"
                batch_size: 2
            """,
            workflowFile.toAbsolutePath(),
            libraryFile.toAbsolutePath()
        );
        Files.writeString(configPath, configYaml);

        when(mockPublisher.publish(anyString())).thenReturn("msg-id");
    }

    // --- 3.1: シーン × プロンプトループ ---

    @Test
    void run_success_publishesCorrectNumberOfTimes() {
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.SUCCESS, code);
        // scene1: batch_size=1, scene2: batch_size=2 → 合計3回
        verify(mockPublisher, times(3)).publish(anyString());
    }

    @Test
    void run_success_callsHealthCheckOnce() {
        ClientRunner runner = new ClientRunner(mockPublisher);

        runner.run(configPath.toString(), QUEUE_URL);

        verify(mockPublisher, times(1)).healthCheck();
    }

    @Test
    void run_publishBodyContainsClientIdAndPrompt() throws Exception {
        ClientRunner runner = new ClientRunner(mockPublisher);

        runner.run(configPath.toString(), QUEUE_URL);

        verify(mockPublisher, atLeastOnce()).publish(argThat(body -> {
            try {
                com.fasterxml.jackson.databind.JsonNode root = new ObjectMapper().readTree(body);
                return root.has("client_id") && root.has("prompt");
            } catch (Exception e) {
                return false;
            }
        }));
    }

    // --- 3.2: 例外マッピング・終了コード ---

    @Test
    void run_healthCheckThrowsQueueDoesNotExist_returnsAuthError() {
        doThrow(QueueDoesNotExistException.builder().message("not found").build())
            .when(mockPublisher).healthCheck();
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.AUTH_ERROR, code);
    }

    @Test
    void run_healthCheckThrowsSdkClientException_returnsAuthError() {
        doThrow(SdkClientException.builder().message("no creds").build())
            .when(mockPublisher).healthCheck();
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.AUTH_ERROR, code);
    }

    @Test
    void run_publishThrowsMessageTooLong_returnsPublishError() {
        when(mockPublisher.publish(anyString()))
            .thenThrow(new SqsPromptPublisher.MessageTooLongException("too long"));
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.PUBLISH_ERROR, code);
    }

    @Test
    void run_publishThrowsSqsException_returnsPublishError() {
        when(mockPublisher.publish(anyString()))
            .thenThrow(SqsException.builder().message("sqs error").statusCode(500).build());
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.PUBLISH_ERROR, code);
    }

    @Test
    void run_configFileNotFound_returnsConfigError() {
        ClientRunner runner = new ClientRunner(mockPublisher);

        ClientRunner.ExitCode code = runner.run("/nonexistent/config.yaml", QUEUE_URL);

        assertEquals(ClientRunner.ExitCode.CONFIG_ERROR, code);
    }

    @Test
    void exitCode_numericValues() {
        assertEquals(0, ClientRunner.ExitCode.SUCCESS.numeric());
        assertEquals(1, ClientRunner.ExitCode.CONFIG_ERROR.numeric());
        assertEquals(2, ClientRunner.ExitCode.ARGUMENT_ERROR.numeric());
        assertEquals(3, ClientRunner.ExitCode.AUTH_ERROR.numeric());
        assertEquals(4, ClientRunner.ExitCode.PUBLISH_ERROR.numeric());
        assertEquals(9, ClientRunner.ExitCode.UNEXPECTED.numeric());
    }
}
