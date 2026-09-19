package com.memorylayer.api.ask;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.Citation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievedReference;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AskCitationMapperTest {

    private static RetrievedReference textReference(String documentId, String text) {
        return RetrievedReference.builder()
                .content(RetrievalResultContent.builder().text(text).build())
                .metadata(Map.of("documentId", Document.fromString(documentId)))
                .build();
    }

    private static RetrievedReference timedReference(String documentId, String text, long startMs, long endMs) {
        return RetrievedReference.builder()
                .content(RetrievalResultContent.builder().text(text).build())
                .metadata(Map.of(
                        "documentId", Document.fromString(documentId),
                        "x-amz-bedrock-kb-chunk-start-time-in-millis", Document.fromNumber(startMs),
                        "x-amz-bedrock-kb-chunk-end-time-in-millis", Document.fromNumber(endMs)))
                .build();
    }

    private static Citation citationOf(RetrievedReference... refs) {
        return Citation.builder().retrievedReferences(refs).build();
    }

    @Test
    void dedupesRepeatedCitationsOfTheSameDocumentWithNoTimestamp() {
        Citation citation1 = citationOf(textReference("doc-1", "first mention"));
        Citation citation2 = citationOf(textReference("doc-1", "second mention"));

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citation1, citation2));

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).documentId()).isEqualTo("doc-1");
        assertThat(deduped.get(0).snippet()).isEqualTo("first mention");
    }

    @Test
    void preservesSeparateAudioVideoMomentsOfTheSameDocument() {
        // Phase 6 amendment: dedup by (documentId, startMs, endMs), not documentId alone — two
        // different moments of the same video cited in one answer must both survive.
        Citation citation1 = citationOf(timedReference("video-1", "first moment", 0, 2000));
        Citation citation2 = citationOf(timedReference("video-1", "second moment", 5000, 7000));

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citation1, citation2));

        assertThat(deduped).hasSize(2);
        assertThat(deduped.get(0).mediaTimestamp().startMs()).isEqualTo(0L);
        assertThat(deduped.get(0).mediaTimestamp().endMs()).isEqualTo(2000L);
        assertThat(deduped.get(1).mediaTimestamp().startMs()).isEqualTo(5000L);
        assertThat(deduped.get(1).mediaTimestamp().endMs()).isEqualTo(7000L);
    }

    @Test
    void dedupesTheSameMomentCitedTwice() {
        Citation citation1 = citationOf(timedReference("video-1", "the moment", 0, 2000));
        Citation citation2 = citationOf(timedReference("video-1", "the moment again", 0, 2000));

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citation1, citation2));

        assertThat(deduped).hasSize(1);
    }

    @Test
    void flattensMultipleRetrievedReferencesWithinASingleCitation() {
        Citation citation = citationOf(
                textReference("doc-1", "from doc 1"),
                textReference("doc-2", "from doc 2"));

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citation));

        assertThat(deduped).extracting(AskCitationMapper.DedupedCitation::documentId)
                .containsExactly("doc-1", "doc-2");
    }

    @Test
    void skipsCitationsWithNoRetrievedReferences() {
        Citation citation = Citation.builder().build();

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citation));

        assertThat(deduped).isEmpty();
    }

    @Test
    void skipsAReferenceWhoseDocumentIdCannotBeResolved() {
        RetrievedReference reference = RetrievedReference.builder()
                .content(RetrievalResultContent.builder().text("orphan").build())
                .metadata(Map.of())
                .build();

        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(List.of(citationOf(reference)));

        assertThat(deduped).isEmpty();
    }
}
