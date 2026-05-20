package com.github.us_aito.t2iclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class S3KeySanitizerTest {

  @Test
  void slashIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo/bar"));
  }

  @Test
  void backslashIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo\\bar"));
  }

  @Test
  void colonIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo:bar"));
  }

  @Test
  void asteriskIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo*bar"));
  }

  @Test
  void questionMarkIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo?bar"));
  }

  @Test
  void doubleQuoteIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo\"bar"));
  }

  @Test
  void lessThanIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo<bar"));
  }

  @Test
  void greaterThanIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo>bar"));
  }

  @Test
  void pipeIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo|bar"));
  }

  @Test
  void halfWidthSpaceIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo bar"));
  }

  @Test
  void fullWidthSpaceIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo　bar"));
  }

  @Test
  void tabIsReplacedWithUnderscore() {
    assertEquals("foo_bar", S3KeySanitizer.sanitize("foo\tbar"));
  }

  @Test
  void japaneseCharactersArePreserved() {
    assertEquals("夕焼け_海岸", S3KeySanitizer.sanitize("夕焼け/海岸"));
  }

  @Test
  void alphanumericIsPreserved() {
    assertEquals("scene_01", S3KeySanitizer.sanitize("scene_01"));
  }

  @Test
  void underscoreAndHyphenArePreserved() {
    assertEquals("foo-bar_baz", S3KeySanitizer.sanitize("foo-bar_baz"));
  }

  @Test
  void nullInputReturnsUnderscore() {
    assertEquals("_", S3KeySanitizer.sanitize(null));
  }

  @Test
  void emptyInputReturnsUnderscore() {
    assertEquals("_", S3KeySanitizer.sanitize(""));
  }

  @Test
  void multipleSpecialCharsAreReplaced() {
    assertEquals("a_b_c_d", S3KeySanitizer.sanitize("a/b\\c:d"));
  }

  @Test
  void pathTraversalPatternIsSanitized() {
    String result = S3KeySanitizer.sanitize("../escape");
    assertNotNull(result);
    // '/' is replaced, so no path-separator survives
    org.junit.jupiter.api.Assertions.assertFalse(result.contains("/"));
    org.junit.jupiter.api.Assertions.assertFalse(result.contains("\\"));
  }

  @Test
  void idempotency() {
    String[] inputs = {
        "foo/bar",
        "a\\b:c*d?e\"f<g>h|i j　k",
        "夕焼け/海岸",
        "scene_01",
        "",
        null,
        "../escape",
    };
    for (String input : inputs) {
      String once = S3KeySanitizer.sanitize(input);
      String twice = S3KeySanitizer.sanitize(once);
      assertEquals(once, twice, "Idempotency violated for input: " + input);
    }
  }
}
