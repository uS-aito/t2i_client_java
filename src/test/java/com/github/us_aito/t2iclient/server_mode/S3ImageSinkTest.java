package com.github.us_aito.t2iclient.server_mode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ImageSinkTest {

    private static final S3Destination DEST = S3Destination.parse("s3://my-bucket/results");

    @Mock
    private S3Client mockS3Client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // --- put ---

    @Test
    void put_callsPutObjectWithCorrectBucketAndKey() {
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        S3ImageSink sink = new S3ImageSink(mockS3Client, DEST);
        byte[] bytes = new byte[]{1, 2, 3};
        sink.put("results/img.png", bytes);

        verify(mockS3Client).putObject(
            argThat((PutObjectRequest req) ->
                req.bucket().equals("my-bucket") && req.key().equals("results/img.png")),
            any(RequestBody.class)
        );
    }

    @Test
    void put_bytesPassedThrough_callsPutObjectOnce() {
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        S3ImageSink sink = new S3ImageSink(mockS3Client, DEST);
        byte[] bytes = new byte[]{10, 20, 30, 40};
        sink.put("key", bytes);

        verify(mockS3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // --- healthCheck ---

    @Test
    void healthCheck_callsHeadBucket() {
        when(mockS3Client.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());

        S3ImageSink sink = new S3ImageSink(mockS3Client, DEST);
        assertDoesNotThrow(sink::healthCheck);

        verify(mockS3Client).headBucket(argThat((HeadBucketRequest req) ->
            req.bucket().equals("my-bucket")));
    }

    @Test
    void healthCheck_noSuchBucket_propagatesException() {
        when(mockS3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        S3ImageSink sink = new S3ImageSink(mockS3Client, DEST);
        assertThrows(NoSuchBucketException.class, sink::healthCheck);
    }

    // --- close ---

    @Test
    void close_delegatesToS3Client() {
        S3ImageSink sink = new S3ImageSink(mockS3Client, DEST);
        sink.close();
        verify(mockS3Client).close();
    }
}
