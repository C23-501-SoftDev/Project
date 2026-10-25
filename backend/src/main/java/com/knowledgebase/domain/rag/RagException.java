package com.knowledgebase.domain.rag;

/** Stable public failure classification. Provider messages never cross the REST boundary. */
public class RagException extends RuntimeException {
    public enum Code { DISABLED, INVALID_REQUEST, RATE_LIMITED, PROVIDER_UNAVAILABLE, SOURCES_CHANGED, INDEX_UNAVAILABLE }
    private final Code code;
    public RagException(Code code) { this(code, code.name()); }
    public RagException(Code code, String message) { super(message); this.code = code; }
    public Code getCode() { return code; }
}
