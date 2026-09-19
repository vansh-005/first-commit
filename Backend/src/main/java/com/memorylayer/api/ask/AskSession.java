package com.memorylayer.api.ask;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

/**
 * Maps an application-issued, opaque {@code sessionId} to the underlying Bedrock
 * {@code RetrieveAndGenerate} session, scoped to the owning user's own DynamoDB partition.
 * Docs/DATA_MODEL.md §15a (Phase 6 amendment).
 *
 * <p>The frontend and the wire API only ever see the application {@code sessionId}
 * ({@code SK}'s suffix) — never {@link #bedrockSessionId}. Resolving a follow-up turn always
 * goes through {@code PK = USER#<authenticated sub>}, so a session created by one user simply
 * does not exist from another user's lookup, regardless of whether they somehow learned or
 * guessed the application session ID. This is the same structural ownership boundary
 * {@code Document} already relies on (Docs/DATA_MODEL.md §16 AP1).
 *
 * <p><b>Conversational document context (Phase 8):</b> {@link #contextDocumentIds} is a small,
 * server-owned list of documents a conversation has already resolved (today: by the "do I have
 * ...?" find path, which involves no Bedrock generation and therefore no Bedrock session). It lets a
 * deictic follow-up - "explain this assignment", "summarize it" - retrieve from that file instead of
 * being relevance-gated globally. It is written only by the backend from documents it resolved under
 * the authenticated user's own partition; a client can never supply it. {@link #bedrockSessionId} is
 * therefore nullable: a session can exist with context and no Bedrock session yet, and gains one the
 * first time generation runs.
 *
 * <p>Conversation text/history is deliberately not stored here or anywhere else — Bedrock
 * owns the actual conversational state behind {@link #bedrockSessionId}; this item is only a
 * short-lived, TTL-cleaned pointer to it.
 */
@DynamoDbBean
public class AskSession {

    private String pk;
    private String sk;

    private String applicationSessionId;
    private String userId;
    private String bedrockSessionId;
    private List<String> contextDocumentIds;

    private String updatedAt;

    /** DynamoDB TTL attribute (epoch seconds) — bounds how long an abandoned conversation's
     * mapping lingers, independent of whatever TTL Bedrock applies to the underlying session
     * itself (undocumented — Docs/ARCHITECTURE.md §8.2 explicitly says not to assume one). */
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

    public String getApplicationSessionId() {
        return applicationSessionId;
    }

    public void setApplicationSessionId(String applicationSessionId) {
        this.applicationSessionId = applicationSessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBedrockSessionId() {
        return bedrockSessionId;
    }

    public void setBedrockSessionId(String bedrockSessionId) {
        this.bedrockSessionId = bedrockSessionId;
    }

    /** Documents this conversation has resolved; null/empty when none. */
    public List<String> getContextDocumentIds() {
        return contextDocumentIds;
    }

    public void setContextDocumentIds(List<String> contextDocumentIds) {
        this.contextDocumentIds = contextDocumentIds;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("expiresAt")
    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
