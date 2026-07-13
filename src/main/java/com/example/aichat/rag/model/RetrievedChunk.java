package com.example.aichat.rag.model;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

public record RetrievedChunk(String text, Metadata metadata, double score) {

    public static RetrievedChunk fromSegment(TextSegment segment, double score) {
        return new RetrievedChunk(segment.text(), segment.metadata(), score);
    }

    public TextSegment toSegment() {
        return TextSegment.from(text, metadata);
    }
}
