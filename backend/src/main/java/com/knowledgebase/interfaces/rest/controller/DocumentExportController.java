package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.application.service.MarkdownService;
import com.knowledgebase.domain.model.Document;
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
@Tag(name = "Document Export", description = "Экспорт документов в HTML, PDF, DOCX")
public class DocumentExportController {

    private static final MediaType MEDIA_DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentService documentService;
    private final MarkdownService markdownService;

    public DocumentExportController(DocumentService documentService, MarkdownService markdownService) {
        this.documentService = documentService;
        this.markdownService = markdownService;
    }

    @GetMapping("/html")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Экспорт в HTML", description = "Возвращает документ в виде HTML-страницы")
    public ResponseEntity<byte[]> exportHtml(@PathVariable Long id) {
        Document doc = documentService.getDocumentById(id);
        String markdown = documentService.getDocumentContent(doc);
        String bodyHtml = markdownService.toHtml(markdown);
        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>" +
                "<title>" + escapeHtml(doc.getTitle()) + "</title></head><body>" +
                "<h1>" + escapeHtml(doc.getTitle()) + "</h1>" + bodyHtml + "</body></html>";
        byte[] bytes = fullHtml.getBytes(StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        addContentDispositionHeader(builder, doc.getTitle(), "html");
        return builder.body(bytes);
    }

    @GetMapping("/pdf")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Экспорт в PDF", description = "Конвертирует Markdown в PDF через HTML-рендеринг")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) throws IOException {
        Document doc = documentService.getDocumentById(id);
        String markdown = documentService.getDocumentContent(doc);
        byte[] pdf = markdownService.toPdf(doc.getTitle(), markdown);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF);
        addContentDispositionHeader(builder, doc.getTitle(), "pdf");
        return builder.body(pdf);
    }

    @GetMapping("/docx")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Экспорт в DOCX", description = "Конвертирует Markdown в документ Word (.docx)")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long id) throws IOException {
        Document doc = documentService.getDocumentById(id);
        String markdown = documentService.getDocumentContent(doc);
        byte[] docx = markdownService.toDocx(doc.getTitle(), markdown);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MEDIA_DOCX);
        addContentDispositionHeader(builder, doc.getTitle(), "docx");
        return builder.body(docx);
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

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
