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

import java.io.ByteArrayOutputStream;
import java.io.File;
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
        return renderer.render(document);
    }

    public byte[] toPdf(String title, String markdown) throws IOException {
        String bodyHtml = toHtml(markdown != null ? markdown : "");
        String fullHtml = buildPdfHtml(title, bodyHtml);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        registerDejaVuFonts(builder);
        builder.withHtmlContent(fullHtml, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    private void registerDejaVuFonts(PdfRendererBuilder builder) {
        String[] searchDirs = {
            "/usr/share/fonts/truetype/dejavu/",
            "/usr/share/fonts/dejavu/",
            "/usr/share/fonts/truetype/DejaVu/"
        };
        for (String dir : searchDirs) {
            File regular = new File(dir + "DejaVuSans.ttf");
            if (!regular.exists()) continue;
            try {
                builder.useFont(regular, "DejaVu Sans", 400,
                        BaseRendererBuilder.FontStyle.NORMAL, true);
                File bold = new File(dir + "DejaVuSans-Bold.ttf");
                if (bold.exists()) builder.useFont(bold, "DejaVu Sans", 700,
                        BaseRendererBuilder.FontStyle.NORMAL, false);
                File oblique = new File(dir + "DejaVuSans-Oblique.ttf");
                if (oblique.exists()) builder.useFont(oblique, "DejaVu Sans", 400,
                        BaseRendererBuilder.FontStyle.ITALIC, false);
            } catch (Exception ignored) {}
            return;
        }
    }

    public byte[] toDocx(String title, String markdown) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            addHeading(doc, title, 1);
            Node ast = parser.parse(markdown != null ? markdown : "");
            visitBlock(doc, ast);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ── DOCX AST visitor ──────────────────────────────────────────────────────

    private void visitBlock(XWPFDocument doc,
                            com.vladsch.flexmark.util.ast.Node parent) {
        for (com.vladsch.flexmark.util.ast.Node node : parent.getChildren()) {
            if (node instanceof com.vladsch.flexmark.ast.Heading h) {
                XWPFParagraph para = doc.createParagraph();
                applyHeadingStyle(para, h.getLevel());
                fillInline(para, h, false, false);

            } else if (node instanceof com.vladsch.flexmark.ast.Paragraph) {
                XWPFParagraph para = doc.createParagraph();
                fillInline(para, node, false, false);

            } else if (node instanceof com.vladsch.flexmark.ast.FencedCodeBlock fb) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setFontFamily("Courier New");
                run.setFontSize(10);
                run.setText(fb.getContentChars().toString().stripTrailing());

            } else if (node instanceof com.vladsch.flexmark.ast.IndentedCodeBlock ib) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setFontFamily("Courier New");
                run.setFontSize(10);
                run.setText(ib.getContentChars().toString().stripTrailing());

            } else if (node instanceof com.vladsch.flexmark.ast.BulletList) {
                visitListItems(doc, node, false, new int[]{0});

            } else if (node instanceof com.vladsch.flexmark.ast.OrderedList) {
                visitListItems(doc, node, true, new int[]{0});

            } else if (node instanceof com.vladsch.flexmark.ast.ThematicBreak) {
                XWPFParagraph para = doc.createParagraph();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr =
                        para.getCTP().addNewPPr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr pBdr =
                        ppr.addNewPBdr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border =
                        pBdr.addNewBottom();
                border.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                border.setSz(java.math.BigInteger.valueOf(6));

            } else {
                visitBlock(doc, node);
            }
        }
    }

    private void visitListItems(XWPFDocument doc,
                                com.vladsch.flexmark.util.ast.Node listNode,
                                boolean ordered,
                                int[] counter) {
        for (com.vladsch.flexmark.util.ast.Node item : listNode.getChildren()) {
            if (item instanceof com.vladsch.flexmark.ast.BulletListItem
                    || item instanceof com.vladsch.flexmark.ast.OrderedListItem) {
                counter[0]++;
                String prefix = ordered ? counter[0] + ". " : "• ";
                XWPFParagraph para = doc.createParagraph();
                para.setIndentationLeft(360);
                XWPFRun prefixRun = para.createRun();
                prefixRun.setText(prefix);
                for (com.vladsch.flexmark.util.ast.Node child : item.getChildren()) {
                    if (child instanceof com.vladsch.flexmark.ast.Paragraph) {
                        fillInline(para, child, false, false);
                    }
                }
            }
        }
    }

    private void fillInline(XWPFParagraph para,
                             com.vladsch.flexmark.util.ast.Node node,
                             boolean bold, boolean italic) {
        for (com.vladsch.flexmark.util.ast.Node child : node.getChildren()) {
            if (child instanceof com.vladsch.flexmark.ast.Text) {
                XWPFRun run = para.createRun();
                run.setBold(bold);
                run.setItalic(italic);
                run.setText(child.getChars().toString());

            } else if (child instanceof com.vladsch.flexmark.ast.StrongEmphasis) {
                fillInline(para, child, true, italic);

            } else if (child instanceof com.vladsch.flexmark.ast.Emphasis) {
                fillInline(para, child, bold, true);

            } else if (child instanceof com.vladsch.flexmark.ast.Code c) {
                XWPFRun run = para.createRun();
                run.setFontFamily("Courier New");
                run.setFontSize(10);
                run.setText(c.getText().toString());

            } else if (child instanceof com.vladsch.flexmark.ast.HardLineBreak) {
                XWPFRun run = para.createRun();
                run.addBreak();

            } else if (child instanceof com.vladsch.flexmark.ast.SoftLineBreak) {
                XWPFRun run = para.createRun();
                run.setText(" ");

            } else {
                fillInline(para, child, bold, italic);
            }
        }
    }

    private void addHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph para = doc.createParagraph();
        applyHeadingStyle(para, level);
        XWPFRun run = para.createRun();
        run.setBold(true);
        run.setText(text);
    }

    private void applyHeadingStyle(XWPFParagraph para, int level) {
        XWPFRun run = para.createRun();
        run.setBold(true);
        int fontSize = switch (level) {
            case 1 -> 24;
            case 2 -> 20;
            case 3 -> 16;
            default -> 14;
        };
        run.setFontSize(fontSize);
        // Удаляем только что созданный пустой run — он будет добавлен caller'ом
        para.removeRun(para.getRuns().indexOf(run));
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
               "body { font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 11pt; line-height: 1.6; color: #333; }" +
               ".doc-title { font-size: 22pt; border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 24px; }" +
               "h1 { font-size: 18pt; } h2 { font-size: 15pt; } h3 { font-size: 13pt; }" +
               "pre { background: #f4f4f4; border: 1px solid #ddd; padding: 12px; font-size: 9pt; word-wrap: break-word; }" +
               "code { font-family: \"Courier New\", monospace; background: #f4f4f4; padding: 1px 4px; font-size: 9pt; }" +
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
