package com.memorylayer.api.search;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;

/**
 * The single source of truth for the tenant-isolation retrieval filter, shared by
 * {@code Retrieve} ({@code /search}) and {@code RetrieveAndGenerate} ({@code /ask}, Phase 6)
 * — confirmed via {@code javap} and a live call that both APIs' retrieval configuration take
 * the identical {@code RetrievalFilter} shape. This is deliberately not duplicated per
 * endpoint: it is the one line that enforces {@code userId == authenticated JWT.sub}
 * server-side (Docs/DATA_MODEL.md §10, AGENTS.md "Bedrock retrieval").
 */
public final class RetrievalFilters {

    private RetrievalFilters() {
    }

    public static RetrievalFilter forUser(String userId) {
        return RetrievalFilter.builder()
                .equalsValue(FilterAttribute.builder()
                        .key("userId")
                        .value(Document.fromString(userId))
                        .build())
                .build();
    }
}
