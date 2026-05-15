package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class S3DestinationTest {

    // --- parse: 成功ケース ---

    @Test
    void parse_bucketOnly_prefixEmpty() {
        S3Destination dest = S3Destination.parse("s3://my-bucket");
        assertEquals("my-bucket", dest.bucket());
        assertEquals("", dest.prefix());
    }

    @Test
    void parse_bucketWithTrailingSlash_prefixEmpty() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/");
        assertEquals("my-bucket", dest.bucket());
        assertEquals("", dest.prefix());
    }

    @Test
    void parse_bucketWithPrefix_stripsTrailingSlash() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/results/");
        assertEquals("my-bucket", dest.bucket());
        assertEquals("results", dest.prefix());
    }

    @Test
    void parse_bucketWithPrefixNoTrailingSlash_ok() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/results");
        assertEquals("my-bucket", dest.bucket());
        assertEquals("results", dest.prefix());
    }

    @Test
    void parse_bucketWithNestedPrefix_ok() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/a/b/c");
        assertEquals("my-bucket", dest.bucket());
        assertEquals("a/b/c", dest.prefix());
    }

    // --- parse: 失敗ケース ---

    @Test
    void parse_nullUri_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse(null));
    }

    @Test
    void parse_noScheme_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("my-bucket/results"));
    }

    @Test
    void parse_wrongScheme_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("http://my-bucket/results"));
    }

    @Test
    void parse_emptyBucket_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("s3:///prefix"));
    }

    @Test
    void parse_bucketTooShort_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("s3://ab"));
    }

    @Test
    void parse_bucketWithUppercase_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("s3://MyBucket/prefix"));
    }

    @Test
    void parse_bucketWithUnderscore_throws() {
        assertThrows(S3Destination.InvalidS3UriException.class, () -> S3Destination.parse("s3://my_bucket/prefix"));
    }

    // --- buildKey ---

    @Test
    void buildKey_withPrefix_concatsWithSlash() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/results");
        assertEquals("results/img.png", dest.buildKey("img.png"));
    }

    @Test
    void buildKey_withPrefix_nestedRelativePath() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/results");
        assertEquals("results/sub/img.png", dest.buildKey("sub/img.png"));
    }

    @Test
    void buildKey_noPrefix_noLeadingSlash() {
        S3Destination dest = S3Destination.parse("s3://my-bucket");
        assertEquals("img.png", dest.buildKey("img.png"));
    }

    @Test
    void buildKey_prefixWithTrailingSlash_noDoubleSlash() {
        S3Destination dest = S3Destination.parse("s3://my-bucket/results/");
        assertEquals("results/img.png", dest.buildKey("img.png"));
    }
}
