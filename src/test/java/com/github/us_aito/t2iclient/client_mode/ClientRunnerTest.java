package com.github.us_aito.t2iclient.client_mode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
              project_name: "demo_project"
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
    void run_publishBodyContainsNewEnvelopeFields() throws Exception {
        ClientRunner runner = new ClientRunner(mockPublisher);

        runner.run(configPath.toString(), QUEUE_URL);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(3)).publish(bodyCaptor.capture());

        ObjectMapper mapper = new ObjectMapper();
        Pattern serialPattern = Pattern.compile("^\\d{8}-\\d{6}$");
        for (String body : bodyCaptor.getAllValues()) {
            JsonNode root = mapper.readTree(body);
            assertEquals("demo_project", root.path("project_name").asText(),
                "project_name must equal the fixture value");
            assertTrue(root.path("scene_name").isTextual()
                    && !root.path("scene_name").asText().isEmpty(),
                "scene_name must be a non-empty string");
            assertTrue(serialPattern.matcher(root.path("serial").asText()).matches(),
                "serial must match yyyyMMdd-HHmmss: " + root.path("serial").asText());
            assertTrue(root.path("batch_index").isIntegralNumber(),
                "batch_index must be a JSON integer");
            JsonNode payload = root.path("comfyui_payload");
            assertTrue(payload.isObject(), "comfyui_payload must be a JSON object");
            assertTrue(payload.path("client_id").isTextual(),
                "comfyui_payload.client_id must be present");
            assertTrue(payload.path("prompt").isObject(),
                "comfyui_payload.prompt must be present and an object");
        }
    }

    @Test
    void run_publishUsesSameSerialAcrossAllMessages() throws Exception {
        ClientRunner runner = new ClientRunner(mockPublisher);

        runner.run(configPath.toString(), QUEUE_URL);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(3)).publish(bodyCaptor.capture());

        ObjectMapper mapper = new ObjectMapper();
        String firstSerial = mapper.readTree(bodyCaptor.getAllValues().get(0)).path("serial").asText();
        for (String body : bodyCaptor.getAllValues()) {
            assertEquals(firstSerial, mapper.readTree(body).path("serial").asText(),
                "all published messages must share the same serial");
        }
    }

    @Test
    void run_batchSizeN_publishesBatchIndex0ToNMinus1PerScene() throws Exception {
        ClientRunner runner = new ClientRunner(mockPublisher);

        runner.run(configPath.toString(), QUEUE_URL);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(3)).publish(bodyCaptor.capture());

        ObjectMapper mapper = new ObjectMapper();
        List<Integer> scene1Indices = new ArrayList<>();
        List<Integer> scene2Indices = new ArrayList<>();
        for (String body : bodyCaptor.getAllValues()) {
            JsonNode root = mapper.readTree(body);
            String sceneName = root.path("scene_name").asText();
            int batchIndex = root.path("batch_index").asInt();
            if ("scene1".equals(sceneName)) {
                scene1Indices.add(batchIndex);
            } else if ("scene2".equals(sceneName)) {
                scene2Indices.add(batchIndex);
            }
        }

        assertEquals(List.of(0), scene1Indices,
            "scene1 (batch_size=1) must publish batch_index=0 only");
        assertEquals(List.of(0, 1), scene2Indices,
            "scene2 (batch_size=2) must publish batch_index 0..1 in order");
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

    // --- 3.2 (task): base_negative_prompt の解決と連結 ---

    @Test
    void run_baseNegativeInDefaults_composedIntoNegativeNode() throws Exception {
        // default_prompts に base_negative_prompt を追加したconfig
        Path workflowFile = tempDir.resolve("workflow_bn.json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode workflow = mapper.createObjectNode();
        for (int id : new int[]{1, 2, 3, 4, 5}) {
            workflow.withObject(String.valueOf(id)).withObject("inputs");
        }
        Files.writeString(workflowFile, mapper.writeValueAsString(workflow));

        Path libraryFile2 = tempDir.resolve("library2.yaml");
        Files.writeString(libraryFile2, "pose:\n  - standing\n");

        Path cfg = tempDir.resolve("config_bn.yaml");
        String configYaml = String.format("""
            comfyui_config:
              server_address: localhost:8188
              client_id: test-client
            workflow_config:
              project_name: "bn_project"
              workflow_json_path: %s
              image_output_path: /tmp/output
              library_file_path: %s
              seed_node_id: 1
              batch_size_node_id: 2
              negative_prompt_node_id: 3
              positive_prompt_node_id: 4
              environment_prompt_node_id: 5
              default_prompts:
                base_negative_prompt: "base_neg"
                negative_prompt: "neg"
                positive_prompt: "a <pose> scene"
                batch_size: 1
            scenes:
              - name: "sceneA"
                positive_prompt: "a <pose> person"
                batch_size: 1
            """,
            workflowFile.toAbsolutePath(),
            libraryFile2.toAbsolutePath()
        );
        Files.writeString(cfg, configYaml);

        ClientRunner runner = new ClientRunner(mockPublisher);
        ClientRunner.ExitCode code = runner.run(cfg.toString(), QUEUE_URL);
        assertEquals(ClientRunner.ExitCode.SUCCESS, code);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(1)).publish(captor.capture());

        JsonNode root = mapper.readTree(captor.getValue());
        JsonNode prompt = root.path("comfyui_payload").path("prompt");
        String negativeText = prompt.path("3").path("inputs").path("text").asText();
        assertEquals("base_neg, neg", negativeText,
            "default base_negative_prompt should be prepended to negative_prompt");
    }

    @Test
    void run_baseNegativeInScene_composedIntoNegativeNode() throws Exception {
        // scene に base_negative_prompt を設定したconfig
        Path workflowFile = tempDir.resolve("workflow_bn2.json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode workflow = mapper.createObjectNode();
        for (int id : new int[]{1, 2, 3, 4, 5}) {
            workflow.withObject(String.valueOf(id)).withObject("inputs");
        }
        Files.writeString(workflowFile, mapper.writeValueAsString(workflow));

        Path libraryFile3 = tempDir.resolve("library3.yaml");
        Files.writeString(libraryFile3, "pose:\n  - standing\n");

        Path cfg = tempDir.resolve("config_bn2.yaml");
        String configYaml = String.format("""
            comfyui_config:
              server_address: localhost:8188
              client_id: test-client
            workflow_config:
              project_name: "bn2_project"
              workflow_json_path: %s
              image_output_path: /tmp/output
              library_file_path: %s
              seed_node_id: 1
              batch_size_node_id: 2
              negative_prompt_node_id: 3
              positive_prompt_node_id: 4
              environment_prompt_node_id: 5
              default_prompts:
                negative_prompt: "neg"
                positive_prompt: "a <pose> scene"
                batch_size: 1
            scenes:
              - name: "sceneB"
                base_negative_prompt: "scene_base_neg"
                positive_prompt: "a <pose> person"
                batch_size: 1
            """,
            workflowFile.toAbsolutePath(),
            libraryFile3.toAbsolutePath()
        );
        Files.writeString(cfg, configYaml);

        ClientRunner runner = new ClientRunner(mockPublisher);
        ClientRunner.ExitCode code = runner.run(cfg.toString(), QUEUE_URL);
        assertEquals(ClientRunner.ExitCode.SUCCESS, code);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(1)).publish(captor.capture());

        JsonNode root = mapper.readTree(captor.getValue());
        JsonNode prompt = root.path("comfyui_payload").path("prompt");
        String negativeText = prompt.path("3").path("inputs").path("text").asText();
        assertEquals("scene_base_neg, neg", negativeText,
            "scene-level base_negative_prompt should override default and be prepended");
    }

    @Test
    void run_noBaseNegative_backwardCompatible() throws Exception {
        // base_negative_prompt なし → 既存の negative_prompt がそのまま使われること
        ClientRunner runner = new ClientRunner(mockPublisher);
        ClientRunner.ExitCode code = runner.run(configPath.toString(), QUEUE_URL);
        assertEquals(ClientRunner.ExitCode.SUCCESS, code);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockPublisher, times(3)).publish(captor.capture());

        ObjectMapper mapper = new ObjectMapper();
        // scene1 の最初のメッセージを確認
        JsonNode root = mapper.readTree(captor.getAllValues().get(0));
        JsonNode prompt = root.path("comfyui_payload").path("prompt");
        String negativeText = prompt.path("3").path("inputs").path("text").asText();
        assertEquals("bad", negativeText,
            "without base_negative_prompt, original negative_prompt must be used as-is");
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
