package com.github.us_aito.t2iclient.workflow_manager;

// JUnit (テスト用) のインポート
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Mockito (モック用) のインポート
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

// Java標準のインポート
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

// ★重要：テスト対象クラスと同じパッケージなので、
// パッケージプライベートの record やコンストラクタにアクセスできる
// (WorkflowManager.java をインポートする必要はない)

class WorkflowManagerTest {

    // --- 準備 (Arrange) ---

    // @Mock アノテーションが「偽物の HttpClient」を自動で作成する
    @Mock
    private HttpClient mockHttpClient;

    // @Mock アノテーションが「偽物の HttpResponse」を作成する
    // (PromptResponse はパッケージプライベートなので、<PromptResponse> と書ける)
    @Mock
    private HttpResponse<PromptResponse> mockHttpResponse;

    // テスト対象のクラス
    private WorkflowManager workflowManager;

    // 各テストが実行される「前」に、毎回このメソッドが呼ばれる
    @BeforeEach
    void setUp() {
        // Mockito のアノテーションを初期化
        MockitoAnnotations.openMocks(this);
        
        // ★★★ ここが最重要 ★★★
        // new WorkflowManager() ではなく、偽物の `mockHttpClient` を注入する
        workflowManager = new WorkflowManager(mockHttpClient);
    }

    // --- テストケース ---

    @Test // これがテストメソッドであることを示す
    void testSendPrompt_Success() throws IOException, InterruptedException {
        
        // --- 1. 「偽物の設定」 (Arrange) ---
        
        // テスト用のダミーデータ
        String testServer = "localhost:8080";
        String testClientId = "test-client";
        Map<String, Object> testData = Map.of("node", "test_value");
        String expectedPromptId = "mock-prompt-id-123";

        // `PromptResponse` は record なので new で普通に作れる
        PromptResponse fakePromptResponse = new PromptResponse(expectedPromptId);

        // 「偽物のレスポンス(mockHttpResponse)の body() を呼んだら、
        //   偽物のPromptResponse(fakePromptResponse) を返す」ように設定
        when(mockHttpResponse.body()).thenReturn(fakePromptResponse);

        // 「偽物のクライアント(mockHttpClient)の send() が、
        //   「どんな HttpRequest でも」「どんな BodyHandler でも」呼び出されたら、
        //   上で設定した「偽物のレスポンス(mockHttpResponse)」を返す」
        //   ように設定
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        // --- 2. テスト対象の実行 (Act) ---
        String actualPromptId = workflowManager.sendPrompt(testServer, testClientId, testData);

        // --- 3. 結果の検証 (Assert) ---
        
        // 戻り値(actualPromptId)が、期待した値(expectedPromptId)と一致するか検証
        assertEquals(expectedPromptId, actualPromptId);

        // (おまけ) ちゃんと mockHttpClient の send が1回だけ呼ばれたか検証
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}