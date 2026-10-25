package com.knowledgebase.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.application.service.export.DocumentExportAssets;
import com.knowledgebase.application.service.export.ExportException;
import com.knowledgebase.domain.exception.AccessDeniedException;
import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;
import com.knowledgebase.domain.repository.AttachmentRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentExportServiceTest {
    private final DocumentService documents = mock(DocumentService.class);
    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final AttachmentFileStorageRepository storage = mock(AttachmentFileStorageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final List<Attachment> metadata = new ArrayList<>();
    private final Map<String, byte[]> files = new HashMap<>();
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 10, 26, 0, 0);
    private DocumentExportService exports;
    private Document document;
    private String source;

    @BeforeEach
    void setUp() throws Exception {
        document = Document.restore(1L, "Отчёт", "doc.md", DocumentStatus.PUBLISHED,
                null, 2L, 3L, null, null, null, timestamp, timestamp);
        when(documents.canReadDocument(2L, false, 1L)).thenReturn(true);
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, false, false)));
        when(documents.getDocumentById(1L)).thenReturn(document);
        when(documents.getDocumentContent(any())).thenAnswer(invocation -> source);
        when(attachments.findByDocumentId(1L, false)).thenAnswer(invocation -> List.copyOf(metadata));
        when(storage.open(anyString())).thenAnswer(invocation -> {
            byte[] bytes = files.get(invocation.getArgument(0));
            if (bytes == null) throw new IOException("physical private path unavailable");
            return new ByteArrayInputStream(bytes);
        });
        exports = new DocumentExportService(documents, new MarkdownService(), attachments, storage, new ObjectMapper(), users);
        source = "Body";
    }

    @Test
    void standaloneHtmlEmbedsPngJpegAndSafeDownloadBytesAfterSanitization() throws Exception {
        add(10, "figure.png", png(12, 8));
        add(11, "photo.jpg", image(10, 10, "jpeg"));
        byte[] text = "portable text".getBytes(StandardCharsets.UTF_8);
        add(12, "notes \"<&.txt", text);
        source = "![Figure](/api/attachments/10/download)\n\n<img src=\"/api/attachments/11/download\" onerror=\"alert(1)\"/>\n\n"
                + "[Notes](/api/attachments/12/download)";
        var html = Jsoup.parse(new String(export(DocumentExportService.Format.HTML), StandardCharsets.UTF_8));
        assertEquals(2, html.select("img").size());
        assertTrue(html.select("img").get(0).attr("src").startsWith("data:image/png;base64,"));
        assertTrue(html.select("img").get(1).attr("src").startsWith("data:image/jpeg;base64,"));
        var link = html.selectFirst("a[href]");
        assertTrue(link.attr("href").startsWith("data:application/octet-stream;base64,"));
        assertArrayEquals(text, Base64.getDecoder().decode(link.attr("href").split(",", 2)[1]));
        assertEquals("notes \"<&.txt", link.attr("download"));
        assertTrue(html.select("[onerror],script").isEmpty());
    }

    @Test
    void archiveIncludesAllFilesWithUniqueConfinedPathsAndPortableLinks() throws Exception {
        byte[] png = png(5, 4);
        add(10, "../../same.png", png);
        add(11, "../../same.png", "ordinary attachment".getBytes(StandardCharsets.UTF_8));
        source = "![Image](/api/attachments/10/download)\n\n[File](/api/attachments/11/download)";
        Map<String, byte[]> zip = unzip(export(DocumentExportService.Format.ZIP));
        assertEquals(4, zip.size());
        assertTrue(zip.containsKey("index.html"));
        assertTrue(zip.containsKey("manifest.json"));
        assertTrue(zip.keySet().stream().noneMatch(path -> path.startsWith("/") || path.contains("../") || path.contains("\\")));
        var html = Jsoup.parse(new String(zip.get("index.html"), StandardCharsets.UTF_8));
        String path = html.selectFirst("img").attr("src");
        assertTrue(path.startsWith("attachments/10-"));
        assertArrayEquals(png, zip.get(path));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(zip.get(path))));
        for (var link : html.select("a[href]")) assertTrue(zip.containsKey(link.attr("href")));
        assertFalse(new String(zip.get("manifest.json"), StandardCharsets.UTF_8).contains("document-1"));
    }

    @Test
    void pdfContainsEmbeddedImagesAndReadableCyrillic() throws Exception {
        add(10, "diagram.png", png(160, 80));
        source = "## Раздел\n\n![Диаграмма](/api/attachments/10/download)\n\nТекст после картинки.";
        byte[] exported = export(DocumentExportService.Format.PDF);
        try (PDDocument pdf = PDDocument.load(exported)) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("Текст после картинки."));
            int images = 0;
            for (var page : pdf.getPages()) for (var name : page.getResources().getXObjectNames()) {
                if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                    images++; assertEquals(160, image.getWidth()); assertEquals(80, image.getHeight());
                }
            }
            assertTrue(images > 0, "PDF must contain an actual image XObject");
            BufferedImage rendered = new PDFRenderer(pdf).renderImageWithDPI(0, 96);
            long redPixels = 0;
            for (int y = 0; y < rendered.getHeight(); y++) for (int x = 0; x < rendered.getWidth(); x++) {
                Color pixel = new Color(rendered.getRGB(x, y));
                if (pixel.getRed() > 200 && pixel.getGreen() < 80 && pixel.getBlue() < 110) redPixels++;
            }
            assertTrue(redPixels > 8_000, "The embedded image must be visibly painted on the PDF page");
            String evidence = System.getProperty("export.visual.dir");
            if (evidence != null) {
                Path directory = Files.createDirectories(Path.of(evidence));
                Files.write(directory.resolve("embedded-image.pdf"), exported);
                ImageIO.write(rendered, "png", directory.resolve("embedded-image.png").toFile());
            }
            rendered.flush();
        }
    }

    @Test
    void docxEmbedsMarkdownReferenceAndRawHtmlImagesWithoutLosingTablesAndLists() throws Exception {
        add(10, "diagram.png", png(8, 6));
        source = "## Heading\n\n![inline](/api/attachments/10/download)\n\n![reference][picture]\n\n"
                + "[picture]: /api/attachments/10/download\n\n<img src=\"/api/attachments/10/download\" alt=\"raw\" onerror=\"bad()\"/>\n\n"
                + "| Column |\n| --- |\n| cell ![table](/api/attachments/10/download) |\n\n"
                + "3. first ![list](/api/attachments/10/download)\n4. second\n";
        byte[] exported = export(DocumentExportService.Format.DOCX);
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertFalse(docx.getAllPictures().isEmpty());
            assertNotNull(ImageIO.read(new ByteArrayInputStream(docx.getAllPictures().get(0).getData())));
            assertEquals(4, docx.getParagraphs().stream().mapToInt(DocumentExportServiceTest::pictures).sum());
            var tableCell = docx.getTables().get(0).getRow(1).getCell(0);
            assertTrue(tableCell.getText().contains("cell"));
            assertEquals(1, tableCell.getParagraphs().stream().mapToInt(DocumentExportServiceTest::pictures).sum());
            assertTrue(docx.getParagraphs().stream().anyMatch(p -> "Heading2".equals(p.getStyle())));
            assertTrue(docx.getParagraphs().stream().anyMatch(p -> p.getNumID() != null && pictures(p) == 1));
            var image = docx.getParagraphs().stream().flatMap(p -> p.getRuns().stream())
                    .flatMap(r -> r.getEmbeddedPictures().stream()).findFirst().orElseThrow();
            assertNotNull(docx.getRelationById(image.getCTPicture().getBlipFill().getBlip().getEmbed()));
            assertEquals(org.apache.poi.util.Units.pixelToEMU(8), image.getCTPicture().getSpPr().getXfrm().getExt().getCx());
            assertEquals(org.apache.poi.util.Units.pixelToEMU(6), image.getCTPicture().getSpPr().getXfrm().getExt().getCy());
            assertFalse(new String(unzip(exported).get("word/_rels/document.xml.rels"), StandardCharsets.UTF_8)
                    .contains("TargetMode=\"External\""));
        }
    }

    @Test
    void foreignMissingAndExternalImagesNeverFetchOrExposeForeignBytes() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secret.png", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.start();
        try {
            source = "![foreign](/api/attachments/999/download)\n\n[foreign file](/api/attachments/998/download)\n\n"
                    + "<img src=\"file:///etc/passwd\" alt=\"file\"/>\n\n"
                    + "![external](http://127.0.0.1:" + server.getAddress().getPort() + "/secret.png)";
            for (var format : DocumentExportService.Format.values()) {
                byte[] bytes = export(format);
                assertTrue(bytes.length > 0);
                if (format == DocumentExportService.Format.HTML) {
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    assertTrue(html.contains("Изображение недоступно"));
                    assertFalse(html.contains("/api/attachments/"));
                    assertFalse(html.contains("file:///"));
                    assertTrue(Jsoup.parse(html).select("img").isEmpty());
                }
            }
            assertEquals(0, requests.get());
            verifyNoInteractions(storage);
            verify(attachments, never()).findById(anyLong());
        } finally { server.stop(0); }
    }

    @Test
    void missingImageHasFallbackButRequiredArchiveFileReturnsControlledError() throws Exception {
        Attachment image = add(10, "missing.png", png(4, 4));
        files.remove(image.getStoragePath());
        source = "![lost](/api/attachments/10/download)";
        assertTrue(new String(export(DocumentExportService.Format.HTML), StandardCharsets.UTF_8).contains("Изображение недоступно"));
        ExportException error = assertThrows(ExportException.class, () -> export(DocumentExportService.Format.ZIP));
        assertEquals(ExportException.Code.ATTACHMENT_UNAVAILABLE, error.getCode());
        assertFalse(error.getMessage().contains("physical"));
        assertNull(error.getCause());
    }

    @Test
    void unsupportedOrForgedImageContentFallsBackButStillArchivesOriginalFile() throws Exception {
        add(10, "fake.png", "<svg onload='alert(1)'></svg>".getBytes(StandardCharsets.UTF_8));
        source = "![not png](/api/attachments/10/download)";
        String html = new String(export(DocumentExportService.Format.HTML), StandardCharsets.UTF_8);
        assertTrue(html.contains("Изображение недоступно"));
        assertFalse(html.contains("onload"));
        assertEquals(3, unzip(export(DocumentExportService.Format.ZIP)).size());
    }

    @Test
    void countSizePixelAndSourceLimitsAreEnforced() throws Exception {
        add(10, "too-wide.png", png(6001, 1));
        source = "![wide](/api/attachments/10/download)";
        assertEquals(ExportException.Code.LIMIT_EXCEEDED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.HTML)).getCode());
        metadata.clear();
        for (int i = 0; i <= DocumentExportAssets.MAX_FILES; i++) add(100 + i, "file.txt", new byte[]{1});
        assertThrows(ExportException.class, () -> export(DocumentExportService.Format.ZIP));
        metadata.clear(); source = "x".repeat(1_000_001);
        assertThrows(ExportException.class, () -> export(DocumentExportService.Format.HTML));
    }

    @Test
    void repeatedImageLimitPreventsUnboundedEmbeddingExpansion() throws Exception {
        add(10, "small.png", png(1, 1));
        source = "![x](/api/attachments/10/download)\n\n".repeat(101);
        assertEquals(ExportException.Code.LIMIT_EXCEEDED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.DOCX)).getCode());
    }

    @Test
    void aggregatePixelBudgetRejectsManyIndividuallyAcceptableImages() throws Exception {
        byte[] compressed = png(3200, 3500); // 11.2M per image; three images exceed the 32M total.
        for (long id = 10; id <= 12; id++) add(id, "large-" + id + ".png", compressed);
        source = "![one](/api/attachments/10/download)\n\n![two](/api/attachments/11/download)\n\n![three](/api/attachments/12/download)";
        assertEquals(ExportException.Code.LIMIT_EXCEEDED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.HTML)).getCode());
    }

    @Test
    void docxScalesImagesToPageAndTableColumnWidthWithoutChangingAspectRatio() throws Exception {
        add(10, "wide.png", png(1000, 500));
        source = "![wide](/api/attachments/10/download)\n\n| Left | Right |\n| --- | --- |\n"
                + "| ![cell](/api/attachments/10/download) | text |";
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(export(DocumentExportService.Format.DOCX)))) {
            var pageImage = docx.getParagraphs().stream().flatMap(p -> p.getRuns().stream())
                    .flatMap(r -> r.getEmbeddedPictures().stream()).findFirst().orElseThrow();
            var pageExtent = pageImage.getCTPicture().getSpPr().getXfrm().getExt();
            assertEquals(org.apache.poi.util.Units.pixelToEMU(560), pageExtent.getCx());
            assertEquals(pageExtent.getCx() / 2, pageExtent.getCy());
            var tableImage = docx.getTables().get(0).getRow(1).getCell(0).getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream()).flatMap(r -> r.getEmbeddedPictures().stream()).findFirst().orElseThrow();
            var tableExtent = tableImage.getCTPicture().getSpPr().getXfrm().getExt();
            assertTrue(tableExtent.getCx() < org.apache.poi.util.Units.pixelToEMU(280));
            assertEquals(tableExtent.getCx() / 2, tableExtent.getCy());
        }
    }

    @Test
    void attachmentPathAndActualSizeMustMatchAuthorizedMetadata() throws Exception {
        metadata.add(Attachment.restore(10L, 1L, null, "foreign.txt", "text/plain", 1,
                "attachments/document-2/foreign", 2L, timestamp));
        assertEquals(ExportException.Code.ATTACHMENT_UNAVAILABLE, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.ZIP)).getCode());
        verifyNoInteractions(storage);
        metadata.clear();
        Attachment attachment = add(10, "truncated.txt", new byte[]{1, 2});
        files.put(attachment.getStoragePath(), new byte[]{1});
        assertEquals(ExportException.Code.ATTACHMENT_UNAVAILABLE, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.ZIP)).getCode());
    }

    @Test
    void changedAttachmentMembershipDiscardsRenderedSnapshot() throws Exception {
        Attachment attachment = add(10, "notes.txt", new byte[]{1});
        when(attachments.findByDocumentId(1L, false)).thenReturn(List.of(attachment), List.of());
        assertEquals(ExportException.Code.SOURCE_CHANGED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.ZIP)).getCode());
    }

    @Test
    void authorizationRevocationOrMetadataChangePreventsReturningBytes() throws Exception {
        when(documents.canReadDocument(2L, false, 1L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> export(DocumentExportService.Format.HTML));
        when(documents.canReadDocument(2L, false, 1L)).thenReturn(true, false);
        assertEquals(ExportException.Code.SOURCE_CHANGED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.HTML)).getCode());
    }

    @Test
    void administratorDemotionDuringRenderingCannotExportAnotherAuthorsDraft() throws Exception {
        document = Document.restore(1L, "Private draft", "doc.md", DocumentStatus.DRAFT,
                null, 99L, 3L, null, null, null, timestamp, timestamp);
        when(documents.getDocumentById(1L)).thenReturn(document);
        when(documents.canReadDocument(2L, true, 1L)).thenReturn(true);
        when(documents.canReadDocument(2L, false, 1L)).thenReturn(false);
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, true, false)));
        Attachment attachment = add(10, "private.txt", new byte[]{1});
        when(storage.open(attachment.getStoragePath())).thenAnswer(invocation -> {
            when(users.findById(2L)).thenReturn(Optional.of(user(2L, false, false)));
            return new ByteArrayInputStream(new byte[]{1});
        });

        ExportException error = assertThrows(ExportException.class,
                () -> exports.export(1L, 2L, true, DocumentExportService.Format.ZIP));
        assertEquals(ExportException.Code.SOURCE_CHANGED, error.getCode());
        verify(documents).canReadDocument(2L, true, 1L);
        verify(documents).canReadDocument(2L, false, 1L);
    }

    @Test
    void absentDeletedOrMismatchedPersistedRequesterIsRejectedBeforeReadingContent() {
        for (Optional<User> requester : List.of(Optional.<User>empty(), Optional.of(user(2L, false, true)),
                Optional.of(user(999L, true, false)))) {
            when(users.findById(2L)).thenReturn(requester);
            assertThrows(AccessDeniedException.class,
                    () -> exports.export(1L, 2L, true, DocumentExportService.Format.ZIP));
        }
        verify(documents, never()).getDocumentContent(any());
        verifyNoInteractions(storage);
    }

    @Test
    void requesterDeletionDuringRenderingDiscardsTheExport() throws Exception {
        Attachment attachment = add(10, "private.txt", new byte[]{1});
        when(storage.open(attachment.getStoragePath())).thenAnswer(invocation -> {
            when(users.findById(2L)).thenReturn(Optional.empty());
            return new ByteArrayInputStream(new byte[]{1});
        });
        assertEquals(ExportException.Code.SOURCE_CHANGED, assertThrows(ExportException.class,
                () -> export(DocumentExportService.Format.ZIP)).getCode());
    }

    private byte[] export(DocumentExportService.Format format) throws IOException { return exports.export(1L, 2L, false, format).bytes(); }
    private User user(long id, boolean admin, boolean deleted) {
        return User.restore(id, "reader", "unused", "reader@kb.local", GlobalRole.READER, admin, deleted,
                null, timestamp, timestamp);
    }
    private Attachment add(long id, String filename, byte[] content) {
        String path = "attachments/document-1/" + id + "-stored";
        Attachment attachment = Attachment.restore(id, 1L, null, filename, "application/octet-stream", content.length, path, 2L, timestamp);
        metadata.add(attachment); files.put(path, content); return attachment;
    }
    private static byte[] png(int width, int height) throws IOException { return image(width, height, "png"); }
    private static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(0xff2244));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out)); image.flush(); return out.toByteArray();
    }
    private static int pictures(XWPFParagraph paragraph) {
        return paragraph.getRuns().stream().mapToInt(run -> run.getEmbeddedPictures().size()).sum();
    }
    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) result.put(entry.getName(), zip.readAllBytes());
        }
        return result;
    }
}
