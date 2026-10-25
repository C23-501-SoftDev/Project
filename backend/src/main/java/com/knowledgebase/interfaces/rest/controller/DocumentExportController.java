package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentExportService;
import com.knowledgebase.application.service.export.ExportException;
import com.knowledgebase.domain.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documents/{id}/export")
@Tag(name = "Document Export", description = "Экспорт документов в HTML, PDF, DOCX и ZIP с вложениями")
public class DocumentExportController {

    private static final MediaType MEDIA_DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentExportService exports;

    public DocumentExportController(DocumentExportService exports) { this.exports = exports; }

    @GetMapping("/html")
    @PreAuthorize("@documentService.canReadDocument(principal.id, principal.isAdmin, #id)")
    @Operation(summary = "Экспорт в HTML с локальными изображениями")
    public ResponseEntity<byte[]> exportHtml(@PathVariable Long id, @AuthenticationPrincipal User user) throws IOException {
        return response(id, user, DocumentExportService.Format.HTML, new MediaType("text", "html", StandardCharsets.UTF_8), "html");
    }

    @GetMapping("/pdf")
    @PreAuthorize("@documentService.canReadDocument(principal.id, principal.isAdmin, #id)")
    @Operation(summary = "Экспорт в PDF со встроенными PNG/JPEG")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id, @AuthenticationPrincipal User user) throws IOException {
        return response(id, user, DocumentExportService.Format.PDF, MediaType.APPLICATION_PDF, "pdf");
    }

    @GetMapping("/docx")
    @PreAuthorize("@documentService.canReadDocument(principal.id, principal.isAdmin, #id)")
    @Operation(summary = "Экспорт в DOCX со встроенными PNG/JPEG")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long id, @AuthenticationPrincipal User user) throws IOException {
        return response(id, user, DocumentExportService.Format.DOCX, MEDIA_DOCX, "docx");
    }

    @GetMapping("/zip")
    @PreAuthorize("@documentService.canReadDocument(principal.id, principal.isAdmin, #id)")
    @Operation(summary = "HTML и все вложения документа в автономном ZIP")
    public ResponseEntity<byte[]> exportZip(@PathVariable Long id, @AuthenticationPrincipal User user) throws IOException {
        return response(id, user, DocumentExportService.Format.ZIP, MediaType.parseMediaType("application/zip"), "zip");
    }

    private ResponseEntity<byte[]> response(Long id, User user, DocumentExportService.Format format,
                                            MediaType type, String extension) throws IOException {
        var exported = exports.export(id, user.getId(), user.isAdmin(), format);
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type);
        addContentDispositionHeader(response, exported.title(), extension);
        return response.body(exported.bytes());
    }

    @ExceptionHandler(ExportException.class)
    public ResponseEntity<?> exportFailure(ExportException error) {
        return ResponseEntity.status(error.getCode() == ExportException.Code.LIMIT_EXCEEDED
                        ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.CONFLICT)
                .cacheControl(CacheControl.noStore()).body(Map.of("code", error.getCode().name(), "message", error.getMessage()));
    }

    private void addContentDispositionHeader(ResponseEntity.BodyBuilder builder, String title, String extension) {
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = sanitized + "." + extension;
        // ASCII-safe fallback для старых клиентов
        String asciiFilename = filename.replaceAll("[^\\x20-\\x7E]", "_");
        // RFC 5987 — filename* для поддержки Unicode; filename= — ASCII-fallback
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        builder.header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + encoded);
        // Открываем заголовок для JS fetch()
        builder.header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);
    }

}
