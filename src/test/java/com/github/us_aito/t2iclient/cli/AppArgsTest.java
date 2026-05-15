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
    }

    @Test
    void parse_clientWithSqsAndConfig_returnsClientMode() {
        AppArgs result = AppArgs.parse(new String[]{
            "--client", "--sqs", "https://sqs.ap-northeast-1.amazonaws.com/123456789/my-queue", "config.yaml"
        });

        assertEquals(AppArgs.AppMode.CLIENT, result.mode());
        assertEquals("config.yaml", result.configPath());
        assertEquals("https://sqs.ap-northeast-1.amazonaws.com/123456789/my-queue", result.sqsQueueUrl());
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
    void parse_serverFlag_returnsServerMode() {
        AppArgs result = AppArgs.parse(new String[]{"--server", "config.yaml"});

        assertEquals(AppArgs.AppMode.SERVER, result.mode());
        assertEquals("config.yaml", result.configPath());
    }

    // --- 失敗ケース ---

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
}
