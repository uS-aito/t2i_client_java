package com.github.us_aito.t2iclient.server_mode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
public final class S3ImageSink implements AutoCloseable {

    private final S3Client s3Client;
    private final S3Destination destination;

    public S3ImageSink(S3Destination destination) {
        this.destination = destination;
        this.s3Client = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    S3ImageSink(S3Client s3Client, S3Destination destination) {
        this.s3Client = s3Client;
        this.destination = destination;
    }

    public void healthCheck() {
        s3Client.headBucket(HeadBucketRequest.builder()
            .bucket(destination.bucket())
            .build());
        log.info("S3 health check passed for bucket: {}", destination.bucket());
    }

    public void put(String objectKey, byte[] bytes) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(destination.bucket())
                .key(objectKey)
                .build(),
            RequestBody.fromBytes(bytes)
        );
        log.debug("Uploaded to S3: s3://{}/{}", destination.bucket(), objectKey);
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
