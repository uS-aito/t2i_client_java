package com.github.us_aito.t2iclient.server_mode;

import java.util.regex.Pattern;

public record S3Destination(String bucket, String prefix) {

    private static final Pattern BUCKET_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$");

    public static S3Destination parse(String s3Uri) {
        if (s3Uri == null) {
            throw new InvalidS3UriException("S3 URI must not be null");
        }
        if (!s3Uri.startsWith("s3://")) {
            throw new InvalidS3UriException("S3 URI must start with s3://, got: " + s3Uri);
        }
        String withoutScheme = s3Uri.substring("s3://".length());
        int slashIndex = withoutScheme.indexOf('/');
        String bucket;
        String prefix;
        if (slashIndex < 0) {
            bucket = withoutScheme;
            prefix = "";
        } else {
            bucket = withoutScheme.substring(0, slashIndex);
            prefix = withoutScheme.substring(slashIndex + 1);
            // 末尾スラッシュを除去
            if (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        }
        if (bucket.isEmpty()) {
            throw new InvalidS3UriException("S3 URI bucket must not be empty: " + s3Uri);
        }
        if (!BUCKET_PATTERN.matcher(bucket).matches()) {
            throw new InvalidS3UriException(
                "S3 bucket name is invalid (must be 3-63 chars, lowercase alphanumeric and hyphens): " + bucket);
        }
        return new S3Destination(bucket, prefix);
    }

    public String buildKey(String relativePath) {
        if (prefix == null || prefix.isEmpty()) {
            return relativePath;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return normalizedPrefix + "/" + relativePath;
    }

    public static final class InvalidS3UriException extends RuntimeException {
        public InvalidS3UriException(String message) {
            super(message);
        }
    }
}
