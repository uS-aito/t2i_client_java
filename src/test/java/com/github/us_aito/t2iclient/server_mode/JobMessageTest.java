package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobMessageTest {

    private static final String VALID_BODY = """
        {"client_id":"my-client","prompt":{"3":{"class_type":"KSampler"}}}
        """.strip();

    @Test
    void parse_validJson_returnsJobMessage() {
        JobMessage msg = JobMessage.parse("msg-001", "receipt-001", VALID_BODY);

        assertEquals("msg-001", msg.messageId());
        assertEquals("receipt-001", msg.receiptHandle());
        assertEquals(VALID_BODY, msg.body());
        assertEquals("my-client", msg.clientId());
    }

    @Test
    void parse_missingClientId_clientIdIsNull() {
        String body = """
            {"prompt":{"3":{"class_type":"KSampler"}}}
            """.strip();

        JobMessage msg = JobMessage.parse("msg-002", "receipt-002", body);

        assertNull(msg.clientId());
    }

    @Test
    void parse_missingPrompt_throwsParseException() {
        String body = """
            {"client_id":"my-client"}
            """.strip();

        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("msg-003", "receipt-003", body)
        );
    }

    @Test
    void parse_invalidJson_throwsParseException() {
        String body = "not-json";
        assertThrows(JobMessage.ParseException.class, () ->
            JobMessage.parse("msg-004", "receipt-004", body)
        );
    }

    @Test
    void parse_bodyStringIdentical_noReserialisation() {
        String body = VALID_BODY;
        JobMessage msg = JobMessage.parse("msg-005", "receipt-005", body);
        assertSame(body, msg.body());
    }
}
