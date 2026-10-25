package com.knowledgebase.domain.rag;

/** A safe provider failure: never includes keys, response bodies or supplied document text. */
public class RagProviderException extends RuntimeException {
    public enum Code { DISABLED, UNAVAILABLE, INVALID_RESPONSE }

    private final Code code;

    public RagProviderException(Code code) {
        super(switch (code) {
            case DISABLED -> "RAG provider is disabled or not configured";
            case UNAVAILABLE -> "RAG provider is temporarily unavailable";
            case INVALID_RESPONSE -> "RAG provider returned an invalid response";
        });
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
