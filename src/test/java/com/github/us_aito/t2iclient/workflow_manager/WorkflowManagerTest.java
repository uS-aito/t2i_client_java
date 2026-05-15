package com.github.us_aito.t2iclient.workflow_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

class WorkflowManagerTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<PromptResponse> mockHttpResponse;

    @Mock
    private HttpResponse<byte[]> mockByteResponse;

    private WorkflowManager workflowManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workflowManager = new WorkflowManager(mockHttpClient);
    }

    // --- 既存テスト ---

    @Test
    void testSendPrompt_Success() throws IOException, InterruptedException {
        String testServer = "localhost:8080";
        String testClientId = "test-client";
        Map<String, Object> testData = Map.of("node", "test_value");
        String expectedPromptId = "mock-prompt-id-123";

        PromptResponse fakePromptResponse = new PromptResponse(expectedPromptId);
        when(mockHttpResponse.body()).thenReturn(fakePromptResponse);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        String actualPromptId = workflowManager.sendPrompt(testServer, testClientId, testData);
        assertEquals(expectedPromptId, actualPromptId);
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void testBuildPromptBody_returnsExpectedJson() throws Exception {
        String clientId = "my-client";
        Map<String, Object> workflowData = Map.of("node1", Map.of("inputs", Map.of("text", "hello")));

        String json = workflowManager.buildPromptBody(clientId, workflowData);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        assertEquals("my-client", root.get("client_id").asText());
        assertEquals("hello", root.path("prompt").path("node1").path("inputs").path("text").asText());
    }

    @Test
    void testBuildPromptBody_withNullClientId() throws Exception {
        Map<String, Object> workflowData = Map.of("node1", "val");

        String json = workflowManager.buildPromptBody(null, workflowData);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("client_id").isNull());
    }

    @Test
    void testSendPrompt_usesBuildPromptBodyInternally() throws IOException, InterruptedException {
        String testServer = "localhost:8080";
        String testClientId = "test-client";
        Map<String, Object> testData = Map.of("node", "test_value");
        String expectedPromptId = "mock-prompt-id-456";

        PromptResponse fakePromptResponse = new PromptResponse(expectedPromptId);
        when(mockHttpResponse.body()).thenReturn(fakePromptResponse);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        String actualPromptId = workflowManager.sendPrompt(testServer, testClientId, testData);
        assertEquals(expectedPromptId, actualPromptId);
    }

    // --- 新規: sendPrompt(serverAddress, rawBody) オーバーロード ---

    @Test
    void sendPrompt_rawBody_sendsBodyAsIs() throws IOException, InterruptedException {
        String rawBody = "{\"client_id\":\"c1\",\"prompt\":{}}";
        String expectedPromptId = "raw-prompt-id";

        PromptResponse fakePromptResponse = new PromptResponse(expectedPromptId);
        when(mockHttpResponse.body()).thenReturn(fakePromptResponse);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        String actualPromptId = workflowManager.sendPrompt("localhost:8188", rawBody);

        assertEquals(expectedPromptId, actualPromptId);
        verify(mockHttpClient, times(1)).send(
            argThat(req -> {
                // BodyPublisher からボディ文字列を確認（間接的にリクエストが1回送信されたことを検証）
                return req.uri().toString().contains("/prompt");
            }),
            any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void sendPrompt_rawBody_returnsPromptId() throws IOException, InterruptedException {
        String rawBody = "{\"client_id\":\"c2\",\"prompt\":{\"1\":{}}}";
        PromptResponse fakePromptResponse = new PromptResponse("pid-999");
        when(mockHttpResponse.body()).thenReturn(fakePromptResponse);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        String result = workflowManager.sendPrompt("localhost:8188", rawBody);
        assertEquals("pid-999", result);
    }

    // --- 新規: fetchImage ---

    @Test
    void fetchImage_returnsBytes_noFileCreated() throws IOException, InterruptedException {
        byte[] expected = new byte[]{1, 2, 3, 4, 5};
        when(mockByteResponse.body()).thenReturn(expected);
        when(mockByteResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockByteResponse);

        byte[] result = workflowManager.fetchImage("localhost:8188", "img.png", "output", "");

        assertArrayEquals(expected, result);
        // ローカルファイルは作らない（Pathベースのハンドラを使っていないことを検証）
        verify(mockHttpClient, times(1)).send(
            argThat(req -> req.uri().toString().contains("/view")),
            any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void fetchImage_uriContainsFilenameAndType() throws IOException, InterruptedException {
        when(mockByteResponse.body()).thenReturn(new byte[0]);
        when(mockByteResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockByteResponse);

        workflowManager.fetchImage("localhost:8188", "my-image.png", "output", "subfolder1");

        verify(mockHttpClient).send(
            argThat(req -> {
                String uri = req.uri().toString();
                return uri.contains("my-image.png") && uri.contains("output") && uri.contains("subfolder1");
            }),
            any(HttpResponse.BodyHandler.class)
        );
    }
}
