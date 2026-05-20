package com.github.us_aito.t2iclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

class SerialGeneratorTest {

  @Test
  void generateWithFixedClockProducesExpectedFormat() {
    Instant fixed = LocalDateTime.of(2026, 5, 20, 14, 30, 22)
        .atZone(ZoneId.systemDefault())
        .toInstant();
    Clock clock = Clock.fixed(fixed, ZoneId.systemDefault());

    String serial = SerialGenerator.generate(clock);

    assertEquals("20260520-143022", serial);
  }

  @Test
  void generateWithFixedClockMatchesPattern() {
    Instant fixed = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        .atZone(ZoneId.systemDefault())
        .toInstant();
    Clock clock = Clock.fixed(fixed, ZoneId.systemDefault());

    String serial = SerialGenerator.generate(clock);

    assertTrue(serial.matches("^\\d{8}-\\d{6}$"),
        "Expected yyyyMMdd-HHmmss pattern but got: " + serial);
  }

  @Test
  void generateNowReturnsNonNullWithExpectedFormat() {
    String serial = SerialGenerator.generateNow();

    assertNotNull(serial);
    assertTrue(serial.matches("^\\d{8}-\\d{6}$"),
        "Expected yyyyMMdd-HHmmss pattern but got: " + serial);
  }

  @Test
  void generateNowAndDateTimeAreClose() {
    String before = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    String serial = SerialGenerator.generateNow();
    String after = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

    assertTrue(serial.compareTo(before) >= 0 && serial.compareTo(after) <= 0,
        "Serial " + serial + " should be between " + before + " and " + after);
  }

  @Test
  void sameFixedClockProducesSameSerial() {
    Instant fixed = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        .atZone(ZoneId.systemDefault())
        .toInstant();
    Clock clock = Clock.fixed(fixed, ZoneId.systemDefault());

    assertEquals(SerialGenerator.generate(clock), SerialGenerator.generate(clock));
  }
}
