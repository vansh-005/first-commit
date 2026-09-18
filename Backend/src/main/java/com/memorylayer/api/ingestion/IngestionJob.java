package com.memorylayer.api.ingestion;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

/**
 * Tracks one Bedrock Knowledge Base ingestion job so the status reconciler can find it again.
 * Docs/DATA_MODEL.md §14 (as amended — "Minor Improvements" §2).
 *
 * <p>{@code PK = SYSTEM#INGESTION} for every job record, regardless of which data source
 * started it — the reconciler queries this single partition for all in-progress jobs.
 */
@DynamoDbBean
public class IngestionJob {

    public static final String PARTITION_KEY = "SYSTEM#INGESTION";

    private String pk;
    private String sk;

    private String jobId;
    private String dataSourceId;
    private IngestionJobStatus status;

    /** {@code "userId#documentId"} pairs, not bare document IDs — DocumentRepository has no
     * documentId-only index, so the reconciler needs userId too for a direct lookup. */
    private List<String> documentIds;

    private String startedAt;
    private String updatedAt;
    private String completedAt;
    private String failureReason;

    /** DynamoDB TTL attribute (epoch seconds) — set only once the job reaches a terminal
     * state, per Docs/DATA_MODEL.md's "keep for 7 days after completion" guidance. */
    private Long expiresAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionJobStatus status) {
        this.status = status;
    }

    public List<String> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    @DynamoDbAttribute("expiresAt")
    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
