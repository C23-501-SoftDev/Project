package com.knowledgebase.domain.model;

import java.util.Locale;

/** Text granularity used to compare two document versions. */
public enum DiffAlgorithmType {
    HYBRID, CHARACTER, WORD, LINE;

    public static DiffAlgorithmType fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return HYBRID;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Параметр algorithm должен быть HYBRID, CHARACTER, WORD или LINE");
        }
    }
}
