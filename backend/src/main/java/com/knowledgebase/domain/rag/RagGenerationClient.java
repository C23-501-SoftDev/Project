package com.knowledgebase.domain.rag;

import java.util.List;

/** Provider boundary. Source identifiers refer only to the supplied context list. */
public interface RagGenerationClient {
    GeneratedAnswer generate(String question, List<Context> contexts);

    record Context(int sourceId, String title, String text) { }

    record GeneratedAnswer(String answer, List<Integer> sourceIds, boolean insufficientEvidence) {
        public GeneratedAnswer {
            sourceIds = List.copyOf(sourceIds);
        }
    }
}
