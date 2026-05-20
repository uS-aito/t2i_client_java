package com.github.us_aito.t2iclient.common;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SerialGenerator {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private SerialGenerator() {}

  public static String generateNow() {
    return generate(Clock.system(ZoneId.systemDefault()));
  }

  public static String generate(Clock clock) {
    return LocalDateTime.now(clock).format(FORMATTER);
  }
}
