package com.memorylayer.api.search;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.AudioSegment;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocationType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.VideoSegment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultMapperTest {

    private static KnowledgeBaseRetrievalResult textResult(String documentId, String text, double score) {
        return KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().text(text).build())
                .metadata(Map.of("documentId", Document.fromString(documentId)))
                .score(score)
                .build();
    }

    @Test
    void dedupesMultipleChunksFromTheSameDocumentKeepingTheFirstHighestScoringOne() {
        List<KnowledgeBaseRetrievalResult> results = List.of(
                textResult("doc-1", "first chunk, highest score", 0.9),
                textResult("doc-2", "a different document", 0.8),
                textResult("doc-1", "second chunk, lower score", 0.5));

        List<SearchResultMapper.DedupedMatch> deduped = SearchResultMapper.dedupeAndRank(results, 10);

        assertThat(deduped).hasSize(2);
        assertThat(deduped.get(0).documentId()).isEqualTo("doc-1");
        assertThat(deduped.get(0).snippet()).isEqualTo("first chunk, highest score");
        assertThat(deduped.get(1).documentId()).isEqualTo("doc-2");
    }

    @Test
    void truncatesToTheRequestedLimitAfterDedup() {
        List<KnowledgeBaseRetrievalResult> results = List.of(
                textResult("doc-1", "a", 0.9),
                textResult("doc-2", "b", 0.8),
                textResult("doc-3", "c", 0.7));

        List<SearchResultMapper.DedupedMatch> deduped = SearchResultMapper.dedupeAndRank(results, 2);

        assertThat(deduped).extracting(SearchResultMapper.DedupedMatch::documentId)
                .containsExactly("doc-1", "doc-2");
    }

    @Test
    void resolvesSnippetFromTextContentFirst() {
        KnowledgeBaseRetrievalResult result = textResult("doc-1", "the actual chunk text", 0.9);
        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("the actual chunk text");
    }

    @Test
    void resolvesSnippetFromAudioTranscriptionWhenNoText() {
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder()
                        .audio(AudioSegment.builder().transcription("spoken words here").build())
                        .build())
                .metadata(Map.of("documentId", Document.fromString("doc-1")))
                .build();

        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("spoken words here");
    }

    @Test
    void resolvesSnippetFromVideoSummaryWhenNoText() {
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder()
                        .video(VideoSegment.builder().summary("a video about test patterns").build())
                        .build())
                .metadata(Map.of("documentId", Document.fromString("doc-1")))
                .build();

        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("a video about test patterns");
    }

    @Test
    void resolvesSnippetFromVerifiedDescriptionMetadataForImages() {
        // Images have no usable text content field — only byteContent (not text) — so the
        // verified x-amz-bedrock-kb-description metadata attribute is the real fallback.
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().byteContent("data:image/png;base64,AAAA").build())
                .metadata(Map.of(
                        "documentId", Document.fromString("doc-1"),
                        "x-amz-bedrock-kb-description", Document.fromString("a photo of a blue background")))
                .build();

        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("a photo of a blue background");
    }

    @Test
    void fallsBackToASafeFileTypeMessageWhenNothingElseIsAvailable() {
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().byteContent("data:image/png;base64,AAAA").build())
                .metadata(Map.of("documentId", Document.fromString("doc-1"), "mediaCategory", Document.fromString("IMAGE")))
                .build();

        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("No preview available for this image file.");
    }

    @Test
    void fallsBackToAGenericMessageWhenEvenMediaCategoryIsMissing() {
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().build())
                .metadata(Map.of("documentId", Document.fromString("doc-1")))
                .build();

        assertThat(SearchResultMapper.resolveSnippet(result)).isEqualTo("No preview available for this file.");
    }

    @Test
    void resolvesMediaTimestampFromTheConfirmedLiveMetadataKeys() {
        var timestamp = SearchResultMapper.resolveMediaTimestamp(Map.of(
                "x-amz-bedrock-kb-chunk-start-time-in-millis", Document.fromNumber(1000),
                "x-amz-bedrock-kb-chunk-end-time-in-millis", Document.fromNumber(6360)));

        assertThat(timestamp).isNotNull();
        assertThat(timestamp.startMs()).isEqualTo(1000L);
        assertThat(timestamp.endMs()).isEqualTo(6360L);
    }

    @Test
    void resolvesMediaTimestampFromTheAlternateUnderscoreFormDefensively() {
        var timestamp = SearchResultMapper.resolveMediaTimestamp(Map.of(
                "_media_start_time_ms", Document.fromNumber(500),
                "_media_end_time_ms", Document.fromNumber(2500)));

        assertThat(timestamp).isNotNull();
        assertThat(timestamp.startMs()).isEqualTo(500L);
        assertThat(timestamp.endMs()).isEqualTo(2500L);
    }

    @Test
    void resolvesMediaTimestampWhenTheRealKbSendsDecimalFormattedMillis() {
        // Regression test: the deployed BDA Knowledge Base actually sends these as JSON floats
        // (e.g. 0.0, 6360.0), not integers. Document.fromNumber(double)'s underlying
        // NumberDocument.unwrap() returns the decimal STRING form ("0.0"/"6360.0"), which a
        // naive Long.parseLong on that string throws on — verified live against the deployed
        // KB, where this silently dropped every real audio/video timestamp.
        var timestamp = SearchResultMapper.resolveMediaTimestamp(Map.of(
                "x-amz-bedrock-kb-chunk-start-time-in-millis", Document.fromNumber(0.0),
                "x-amz-bedrock-kb-chunk-end-time-in-millis", Document.fromNumber(6360.0)));

        assertThat(timestamp).isNotNull();
        assertThat(timestamp.startMs()).isEqualTo(0L);
        assertThat(timestamp.endMs()).isEqualTo(6360L);
    }

    @Test
    void resolvesMediaTimestampWhenTheValueIsAStringEncodedNumber() {
        var timestamp = SearchResultMapper.resolveMediaTimestamp(Map.of(
                "x-amz-bedrock-kb-chunk-start-time-in-millis", Document.fromString("100"),
                "x-amz-bedrock-kb-chunk-end-time-in-millis", Document.fromString("200")));

        assertThat(timestamp).isNotNull();
        assertThat(timestamp.startMs()).isEqualTo(100L);
        assertThat(timestamp.endMs()).isEqualTo(200L);
    }

    @Test
    void returnsNullMediaTimestampWhenNeitherFormIsPresent() {
        assertThat(SearchResultMapper.resolveMediaTimestamp(Map.of("documentId", Document.fromString("doc-1"))))
                .isNull();
    }

    @Test
    void extractsDocumentIdFromMetadataAsThePrimaryPath() {
        KnowledgeBaseRetrievalResult result = textResult("doc-1", "text", 0.9);
        assertThat(SearchResultMapper.extractDocumentId(result)).isEqualTo("doc-1");
    }

    @Test
    void fallsBackToParsingTheStagingS3UriWhenMetadataDocumentIdIsMissing() {
        KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().text("text").build())
                .metadata(Map.of())
                .location(RetrievalResultLocation.builder()
                        .type(RetrievalResultLocationType.S3)
                        .s3Location(RetrievalResultS3Location.builder()
                                .uri("s3://uploads-bucket/kb/multimodal/doc-42/photo.jpg")
                                .build())
                        .build())
                .build();

        assertThat(SearchResultMapper.extractDocumentId(result)).isEqualTo("doc-42");
    }
}
