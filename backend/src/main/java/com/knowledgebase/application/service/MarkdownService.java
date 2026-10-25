package com.knowledgebase.application.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.io.IOException;
import java.util.List;

@Service
public class MarkdownService {

    private final MutableDataSet parserOptions;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        parserOptions = new MutableDataSet();
        parserOptions.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create()
        ));
        parser = Parser.builder(parserOptions).build();
        renderer = HtmlRenderer.builder(parserOptions).build();
    }

    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        Node document = parser.parse(markdown);
        Safelist safe = Safelist.relaxed().addTags("del")
                .addProtocols("a", "href", "#").preserveRelativeLinks(true);
        var output = new org.jsoup.nodes.Document.OutputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml).prettyPrint(false);
        return Jsoup.clean(renderer.render(document), "https://knowledge-base.invalid/", safe, output);
    }

    public String toHtmlDocument(String title, String markdown) {
        return "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<title>" + escapeHtml(title) + "</title><style>"
                + "body{font-family:system-ui,sans-serif;max-width:900px;margin:32px auto;padding:0 20px;line-height:1.6}"
                + "table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:8px;text-align:left}"
                + "pre{white-space:pre-wrap;background:#f4f4f4;padding:16px}code{font-family:monospace}"
                + "blockquote{border-left:4px solid #bbb;padding-left:16px;color:#555}img{max-width:100%}"
                + "</style></head><body><h1>" + escapeHtml(title) + "</h1>" + toHtml(markdown) + "</body></html>";
    }

    public byte[] toPdf(String title, String markdown) throws IOException {
        String bodyHtml = toHtml(markdown != null ? markdown : "");
        String fullHtml = buildPdfHtml(title, bodyHtml);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        // PDFBox image loading in 1.0.10 can bypass resource predicates; deny URI resolution too.
        builder.useUriResolver((baseUri, uri) -> null);
        builder.useExternalResourceAccessControl((uri, type) -> false,
                ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        builder.useExternalResourceAccessControl((uri, type) -> false,
                ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        registerPdfFonts(builder);
        builder.withHtmlContent(fullHtml, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    private void registerPdfFonts(PdfRendererBuilder builder) {
        registerFont(builder, "NotoSans-Regular.ttf", "Noto Sans", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "NotoSans-Bold.ttf", "Noto Sans", 700, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "NotoSans-Italic.ttf", "Noto Sans", 400, BaseRendererBuilder.FontStyle.ITALIC);
        registerFont(builder, "NotoSans-BoldItalic.ttf", "Noto Sans", 700, BaseRendererBuilder.FontStyle.ITALIC);
        registerFont(builder, "NotoSansMono-Regular.ttf", "Noto Sans Mono", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "NotoSansMono-Bold.ttf", "Noto Sans Mono", 700, BaseRendererBuilder.FontStyle.NORMAL);
    }

    private void registerFont(PdfRendererBuilder builder, String file, String family, int weight,
                              BaseRendererBuilder.FontStyle style) {
        builder.useFont(() -> Objects.requireNonNull(
                MarkdownService.class.getResourceAsStream("/fonts/" + file), "Missing PDF font: " + file),
                family, weight, style, true);
    }

    public byte[] toDocx(String title, String markdown) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            new DocxRenderer(doc).render(title, parser.parse(markdown != null ? markdown : ""));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ── PDF helpers ───────────────────────────────────────────────────────────

    private String buildPdfHtml(String title, String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>" +
               "<style>" + pdfCss() + "</style></head><body>" +
               "<h1 class=\"doc-title\">" + escapeHtml(title) + "</h1>" +
               bodyHtml +
               "</body></html>";
    }

    private String pdfCss() {
        return "@page { margin: 2cm; }" +
               "body { font-family: 'Noto Sans', sans-serif; font-size: 11pt; line-height: 1.6; color: #333; }" +
               ".doc-title { font-size: 22pt; border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 24px; }" +
               "h1 { font-size: 18pt; } h2 { font-size: 15pt; } h3 { font-size: 13pt; }" +
               "pre { background: #f4f4f4; border: 1px solid #ddd; padding: 12px; font-size: 9pt; word-wrap: break-word; }" +
               "code { font-family: 'Noto Sans Mono', monospace; background: #f4f4f4; padding: 1px 4px; font-size: 9pt; }" +
               "table { border-collapse: collapse; width: 100%; }" +
               "th, td { border: 1px solid #ccc; padding: 6px 10px; }" +
               "th { background: #f4f4f4; font-weight: bold; }" +
               "blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
