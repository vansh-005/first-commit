package com.memorylayer.api.aws;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/** Docs/API.md §10 (upload, 15 min) and §17 (access-url, 5-15 min). */
@Service
public class S3PresignService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final Duration ACCESS_URL_TTL = Duration.ofMinutes(15);

    private final S3Presigner presigner;
    private final String bucketName;

    public S3PresignService(S3Presigner presigner) {
        this.presigner = presigner;
        this.bucketName = System.getenv("UPLOADS_BUCKET_NAME");
    }

    public PresignedPutObjectRequest presignUpload(String s3Key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(objectRequest)
                .build();
        return presigner.presignPutObject(presignRequest);
    }

    public PresignedGetObjectRequest presignDownload(String s3Key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ACCESS_URL_TTL)
                .getObjectRequest(objectRequest)
                .build();
        return presigner.presignGetObject(presignRequest);
    }
}
