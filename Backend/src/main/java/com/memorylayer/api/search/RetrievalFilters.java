package com.memorylayer.api.search;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;

import java.util.Collection;

/**
 * The single source of truth for the tenant-isolation retrieval filter, shared by
 * {@code Retrieve} ({@code /search}) and {@code RetrieveAndGenerate} ({@code /ask}, Phase 6)
 * — confirmed via {@code javap} and a live call that both APIs' retrieval configuration take
 * the identical {@code RetrievalFilter} shape. This is deliberately not duplicated per
 * endpoint: it is the one line that enforces {@code userId == authenticated JWT.sub}
 * server-side (Docs/DATA_MODEL.md §10).
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

    /** The tenant filter <b>AND</b>-ed with a documentId restriction. The tenant clause is always
     * first and never replaced — the restriction can only narrow, never widen. Used by Ask to keep
     * generation context to the documents that passed the relevance gate. */
    public static RetrievalFilter forUserAndDocuments(String userId, Collection<String> documentIds) {
        Document.ListBuilder ids = Document.listBuilder();
        documentIds.forEach(ids::addString);
        RetrievalFilter documentFilter = RetrievalFilter.builder()
                .in(FilterAttribute.builder().key("documentId").value(ids.build()).build())
                .build();
        return RetrievalFilter.builder().andAll(forUser(userId), documentFilter).build();
    }
}
