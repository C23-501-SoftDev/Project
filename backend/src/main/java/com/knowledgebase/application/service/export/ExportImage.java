package com.knowledgebase.application.service.export;

/** Decoded, dimension-checked and re-encoded PNG/JPEG; created only by the document asset resolver. */
public record ExportImage(byte[] bytes, String mediaType, int width, int height) { }
