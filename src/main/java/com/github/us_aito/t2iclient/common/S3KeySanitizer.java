package com.github.us_aito.t2iclient.common;

import java.util.regex.Pattern;

public final class S3KeySanitizer {

  private static final Pattern UNSAFE_CHARS =
      Pattern.compile("[/\\\\:*?\"<>|\\s\\u3000]");

  private S3KeySanitizer() {}

  public static String sanitize(String input) {
    if (input == null || input.isEmpty()) {
      return "_";
    }
    return UNSAFE_CHARS.matcher(input).replaceAll("_");
  }
}
