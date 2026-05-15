package com.github.us_aito.t2iclient.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppArgsTest {

    // --- 成功ケース ---

    @Test
    void parse_positionalArgOnly_returnsFileMode() {
        AppArgs result = AppArgs.parse(new String[]{"config.yaml"});

        assertEquals(AppArgs.AppMode.FILE, result.mode());
        assertEquals("config.yaml", result.configPath());
        assertNull(result.sqsQueueUrl());
        assertNull(result.s3DestinationUri());
    }

    @Test
    void parse_clientWithSqsAndConfig_returnsClientMode() {
        AppArgs result = AppArgs.parse(new String[]{
            "--client", "--sqs", "https://sqs.ap-northeast-1.amazonaws.com/123456789/my-queue", "config.yaml"
        });

        assertEquals(AppArgs.AppMode.CLIENT, result.mode());
        assertEquals("config.yaml", result.configPath());
        assertEquals("https://sqs.ap-northeast-1.amazonaws.com/123456789/my-queue", result.sqsQueueUrl());
        assertNull(result.s3DestinationUri());
    }

    @Test
    void parse_clientFlagsInAnyOrder_returnsClientMode() {
        AppArgs result = AppArgs.parse(new String[]{
            "config.yaml", "--client", "--sqs", "https://sqs.us-east-1.amazonaws.com/111/queue"
        });

        assertEquals(AppArgs.AppMode.CLIENT, result.mode());
        assertEquals("config.yaml", result.configPath());
    }

    @Test
    void parse_serverWithSqsAndS3_returnsServerMode() {
        AppArgs result = AppArgs.parse(new String[]{
            "--server", "--sqs", "https://sqs.ap-northeast-1.amazonaws.com/123/q", "--s3", "s3://my-bucket/results"
        });

        assertEquals(AppArgs.AppMode.SERVER, result.mode());
        assertNull(result.configPath());
        assertEquals("https://sqs.ap-northeast-1.amazonaws.com/123/q", result.sqsQueueUrl());
        assertEquals("s3://my-bucket/results", result.s3DestinationUri());
    }

    // --- 失敗ケース: 既存 ---

    @Test
    void parse_clientWithoutSqs_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--client", "config.yaml"})
        );
        assertTrue(ex.getMessage().contains("--sqs"));
    }

    @Test
    void parse_clientWithoutConfigPath_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--client", "--sqs", "https://sqs.us-east-1.amazonaws.com/111/q"})
        );
        assertTrue(ex.getMessage().toLowerCase().contains("config"));
    }

    @Test
    void parse_clientAndServerTogether_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--client", "--server", "--sqs", "url", "config.yaml"})
        );
        assertTrue(ex.getMessage().contains("--client") || ex.getMessage().contains("--server"));
    }

    @Test
    void parse_unknownFlag_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--unknown", "config.yaml"})
        );
        assertTrue(ex.getMessage().contains("--unknown"));
    }

    @Test
    void parse_noArgs_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{})
        );
        assertNotNull(ex.getMessage());
    }

    // --- 失敗ケース: SERVER モード ---

    @Test
    void parse_serverWithoutSqs_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--server", "--s3", "s3://my-bucket"})
        );
        assertTrue(ex.getMessage().contains("--sqs"));
    }

    @Test
    void parse_serverWithoutS3_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--server", "--sqs", "https://sqs.ap-northeast-1.amazonaws.com/123/q"})
        );
        assertTrue(ex.getMessage().contains("--s3"));
    }

    @Test
    void parse_serverWithConfigPath_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{
                "--server", "--sqs", "https://sqs.ap-northeast-1.amazonaws.com/123/q",
                "--s3", "s3://my-bucket", "config.yaml"
            })
        );
        assertTrue(ex.getMessage().contains("config"));
    }

    @Test
    void parse_serverAndClientTogether_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--client", "--server", "--sqs", "url", "--s3", "s3://b"})
        );
        assertTrue(ex.getMessage().contains("--client") || ex.getMessage().contains("--server"));
    }

    @Test
    void parse_s3WithoutServer_throws() {
        AppArgs.InvalidArgumentException ex = assertThrows(
            AppArgs.InvalidArgumentException.class,
            () -> AppArgs.parse(new String[]{"--s3", "s3://my-bucket", "config.yaml"})
        );
        assertTrue(ex.getMessage().contains("--s3"));
    }
}
