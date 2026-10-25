package com.knowledgebase.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.application.service.export.DocumentExportAssets;
import com.knowledgebase.application.service.export.ExportException;
import com.knowledgebase.application.service.export.ExportImage;
import com.knowledgebase.domain.exception.AccessDeniedException;
import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;
import com.knowledgebase.domain.repository.AttachmentRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Prepares a bounded, authorized snapshot; formats never resolve resources themselves. */
@Service
public class DocumentExportService {
    public enum Format { HTML, PDF, DOCX, ZIP }
    public record Result(String title, byte[] bytes) { }
    private final DocumentService documents;
    private final MarkdownService markdown;
    private final AttachmentRepository attachments;
    private final AttachmentFileStorageRepository storage;
    private final ObjectMapper mapper;
    private final UserRepository users;

    public DocumentExportService(DocumentService documents, MarkdownService markdown, AttachmentRepository attachments,
                                 AttachmentFileStorageRepository storage, ObjectMapper mapper, UserRepository users) {
        this.documents = documents; this.markdown = markdown; this.attachments = attachments;
        this.storage = storage; this.mapper = mapper; this.users = users;
    }

    public Result export(Long id, Long userId, boolean principalIsAdmin, Format format) throws IOException {
        // Principal flags are a request snapshot; access checks use the current persisted identity instead.
        if (!canCurrentlyRead(userId, id)) throw new AccessDeniedException("Нет доступа к документу");
        Document document = documents.getDocumentById(id);
        if (document.getStatus() == DocumentStatus.DELETED) throw new AccessDeniedException("Документ удалён");
        String source = documents.getDocumentContent(document);
        if (source.length() > 1_000_000) throw new ExportException(ExportException.Code.LIMIT_EXCEEDED);
        List<Attachment> metadata = attachments.findByDocumentId(id, false);
        DocumentExportAssets assets = new DocumentExportAssets(id, metadata, storage);
        byte[] bytes;
        if (format == Format.DOCX) {
            bytes = markdown.toDocx(document.getTitle(), source, assets::image,
                    url -> DocumentExportAssets.isAttachmentUrl(url) ? null : url);
        } else {
            if (format == Format.ZIP) for (Attachment attachment : assets.attachments()) assets.file(attachment);
            Map<String, ExportImage> pdfImages = new HashMap<>();
            String body = prepareBody(source, assets, format, pdfImages);
            if (format == Format.PDF) bytes = markdown.toPdfBody(document.getTitle(), body, pdfImages);
            else {
                if (format == Format.ZIP) body = appendAttachmentList(body, assets);
                String html = markdown.htmlDocumentFromBody(document.getTitle(), body);
                bytes = format == Format.ZIP ? zip(html, assets) : html.getBytes(StandardCharsets.UTF_8);
            }
        }
        // Do not return a snapshot after its source/access/attachment membership changes during rendering.
        Document current = documents.getDocumentById(id);
        if (!canCurrentlyRead(userId, id) || current.getStatus() != document.getStatus()
                || !Objects.equals(current.getUpdatedAt(), document.getUpdatedAt())
                || !fingerprints(metadata).equals(fingerprints(attachments.findByDocumentId(id, false)))) {
            throw new ExportException(ExportException.Code.SOURCE_CHANGED);
        }
        return new Result(document.getTitle(), bytes);
    }

    private boolean canCurrentlyRead(Long userId, Long documentId) {
        if (userId == null) return false;
        return users.findById(userId)
                .filter(user -> Objects.equals(userId, user.getId()) && !user.isDeleted() && user.getRole() != null)
                .map(user -> documents.canReadDocument(userId, user.isAdmin(), documentId))
                .orElse(false);
    }

    private Set<String> fingerprints(List<Attachment> metadata) {
        Set<String> result = new HashSet<>();
        for (Attachment a : metadata) result.add(a.getId() + ":" + a.getDocumentId() + ":" + a.getStoragePath()
                + ":" + a.getFilename() + ":" + a.getSizeBytes());
        return result;
    }

    private String prepareBody(String source, DocumentExportAssets assets, Format format, Map<String, ExportImage> pdfImages) {
        var body = Jsoup.parseBodyFragment(markdown.toHtml(source));
        body.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml).prettyPrint(false);
        for (Element image : body.select("img")) {
            String url = image.attr("src");
            ExportImage resolved = assets.image(url);
            if (resolved == null) {
                image.replaceWith(new Element("span").addClass("export-image-unavailable")
                        .text("[Изображение недоступно" + (image.attr("alt").isBlank() ? "" : ": " + image.attr("alt")) + "]"));
            } else {
                String replacement;
                if (format == Format.ZIP) replacement = assets.archivePath(assets.find(url));
                else if (format == Format.PDF) {
                    replacement = "export-image:" + assets.find(url).getId();
                    pdfImages.put(replacement, resolved);
                } else replacement = dataUri(resolved.mediaType(), resolved.bytes());
                image.attr("src", replacement).removeAttr("width").removeAttr("height");
            }
        }
        for (Element link : body.select("a[href]")) {
            String url = link.attr("href");
            if (!DocumentExportAssets.isAttachmentUrl(url)) continue;
            Attachment attachment = assets.find(url);
            if (attachment == null) {
                link.removeAttr("href").appendText(" (вложение недоступно)");
            } else if (format == Format.ZIP) {
                link.attr("href", assets.archivePath(attachment));
            } else if (format == Format.HTML) {
                byte[] file = assets.file(attachment);
                assets.accountEmbedding(file.length);
                link.attr("href", dataUri("application/octet-stream", file)).attr("download", attachment.getFilename());
            } else link.removeAttr("href").appendText(" (файл доступен в ZIP-экспорте)");
        }
        return body.body().html();
    }

    private String appendAttachmentList(String body, DocumentExportAssets assets) {
        var list = new Element("section");
        list.appendElement("h2").text("Вложения");
        Element ul = list.appendElement("ul");
        for (Attachment attachment : assets.attachments()) {
            ul.appendElement("li").appendElement("a").attr("href", assets.archivePath(attachment)).text(attachment.getFilename());
        }
        return body + list.outerHtml();
    }

    private byte[] zip(String html, DocumentExportAssets assets) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Map<String, Object>> manifest = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            entry(zip, "index.html", html.getBytes(StandardCharsets.UTF_8));
            for (Attachment attachment : assets.attachments()) {
                String path = assets.archivePath(attachment);
                byte[] file = assets.file(attachment);
                entry(zip, path, file);
                manifest.add(Map.of("attachmentId", attachment.getId(), "filename", attachment.getFilename(),
                        "path", path, "sizeBytes", file.length));
            }
            entry(zip, "manifest.json", mapper.writeValueAsBytes(Map.of("formatVersion", 1, "attachments", manifest)));
        }
        return output.toByteArray();
    }
    private void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private String dataUri(String mediaType, byte[] bytes) { return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes); }
}
