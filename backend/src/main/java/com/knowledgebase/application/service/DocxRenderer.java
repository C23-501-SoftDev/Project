package com.knowledgebase.application.service;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.io.ByteArrayInputStream;
import com.knowledgebase.application.service.export.ExportImage;
import com.knowledgebase.application.service.export.ExportException;

/** A renderer belongs to one export; no mutable POI state is shared between requests. */
final class DocxRenderer {
    private final XWPFDocument document;
    private final XWPFNumbering numbering;
    private long nextAbstractId;
    private final Function<String, ExportImage> images;
    private final Function<String, String> links;

    DocxRenderer(XWPFDocument document, Function<String, ExportImage> images, Function<String, String> links) {
        this.document = document;
        this.images = images;
        this.links = links;
        this.numbering = document.createNumbering();
        XWPFStyles styles = document.createStyles();
        for (int level = 1; level <= 6; level++) {
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId("Heading" + level);
            style.setType(STStyleType.PARAGRAPH);
            style.addNewName().setVal("heading " + level);
            style.addNewQFormat();
            var properties = style.addNewPPr();
            properties.addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
            properties.addNewKeepNext();
            var run = style.addNewRPr();
            run.addNewB();
            run.addNewSz().setVal(BigInteger.valueOf(switch (level) {
                case 1 -> 48;
                case 2 -> 40;
                case 3 -> 32;
                default -> 28;
            }));
            styles.addStyle(new XWPFStyle(style));
        }
    }

    void render(String title, Node ast) {
        var heading = document.createParagraph();
        heading.setStyle("Heading1");
        heading.createRun().setText(title == null ? "" : title);
        blocks(ast, 0);
    }

    private void blocks(Node parent, int depth) {
        for (Node node : parent.getChildren()) block(node, depth);
    }

    private void block(Node node, int depth) {
        if (node instanceof Heading heading) {
            var paragraph = document.createParagraph();
            paragraph.setStyle("Heading" + heading.getLevel());
            inline(paragraph, node, false, false, false, null);
        } else if (node instanceof Paragraph) {
            inline(document.createParagraph(), node, false, false, false, null);
        } else if (node instanceof FencedCodeBlock code) {
            code(code.getContentChars().toString());
        } else if (node instanceof IndentedCodeBlock code) {
            code(code.getContentChars().toString());
        } else if (node instanceof ListBlock) {
            list(node, depth);
        } else if (node instanceof TableBlock) {
            table(node);
        } else if (node instanceof HtmlBlock) {
            html(document.createParagraph(), node.getChars().toString());
        } else if (node instanceof ThematicBreak) {
            document.createParagraph().setBorderBottom(Borders.SINGLE);
        } else {
            blocks(node, depth);
        }
    }

    private void code(String content) {
        var run = document.createParagraph().createRun();
        run.setFontFamily("Courier New");
        run.setFontSize(10);
        String[] lines = content.replaceFirst("\\R$", "").split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    private void list(Node list, int depth) {
        boolean ordered = list instanceof OrderedList;
        int level = Math.min(depth, 8);
        int start = ordered ? ((OrderedList) list).getStartNumber() : 1;
        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(BigInteger.valueOf(nextAbstractId++));
        var definition = abstractNum.addNewLvl();
        definition.setIlvl(BigInteger.valueOf(level));
        definition.addNewStart().setVal(BigInteger.valueOf(start));
        definition.addNewNumFmt().setVal(ordered ? STNumberFormat.DECIMAL : STNumberFormat.BULLET);
        definition.addNewLvlText().setVal(ordered ? "%" + (level + 1) + "." : "•");
        var indent = definition.addNewPPr().addNewInd();
        indent.setLeft(BigInteger.valueOf(360L * (level + 1)));
        indent.setHanging(BigInteger.valueOf(180));
        BigInteger numId = numbering.addNum(numbering.addAbstractNum(new XWPFAbstractNum(abstractNum)));
        for (Node item : list.getChildren()) {
            boolean first = true;
            for (Node child : item.getChildren()) {
                if (child instanceof Paragraph) {
                    var paragraph = document.createParagraph();
                    if (first) {
                        paragraph.setNumID(numId);
                        paragraph.setNumILvl(BigInteger.valueOf(level));
                        first = false;
                    } else {
                        paragraph.setIndentationLeft(360 * (level + 1));
                    }
                    inline(paragraph, child, false, false, false, null);
                } else {
                    block(child, depth + 1);
                }
            }
        }
    }

    private void table(Node table) {
        List<List<TableCell>> rows = new ArrayList<>();
        for (Node node : table.getDescendants()) {
            if (node instanceof TableRow && !(node.getParent() instanceof TableSeparator)) {
                List<TableCell> cells = new ArrayList<>();
                for (Node cell : node.getChildren()) if (cell instanceof TableCell c) cells.add(c);
                if (!cells.isEmpty()) rows.add(cells);
            }
        }
        if (rows.isEmpty()) return;
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable output = document.createTable(rows.size(), columns);
        output.setWidth("100%");
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                TableCell source = rows.get(r).get(c);
                XWPFTableCell cell = output.getRow(r).getCell(c);
                var paragraph = cell.getParagraphs().get(0);
                if (source.getAlignment() != null) {
                    paragraph.setAlignment(switch (source.getAlignment()) {
                        case CENTER -> ParagraphAlignment.CENTER;
                        case RIGHT -> ParagraphAlignment.RIGHT;
                        default -> ParagraphAlignment.LEFT;
                    });
                }
                if (source.isHeader()) {
                    cell.setColor("F4F4F4");
                    output.getRow(r).setRepeatHeader(true);
                }
                inline(paragraph, source, source.isHeader(), false, false, null);
            }
        }
    }

    private void inline(XWPFParagraph paragraph, Node parent, boolean bold, boolean italic,
                        boolean strike, String link) {
        for (Node child : parent.getChildren()) {
            if (child instanceof Text) {
                text(paragraph, child.getChars().toString(), bold, italic, strike, link);
            } else if (child instanceof StrongEmphasis) {
                inline(paragraph, child, true, italic, strike, link);
            } else if (child instanceof Emphasis) {
                inline(paragraph, child, bold, true, strike, link);
            } else if (child instanceof Strikethrough) {
                inline(paragraph, child, bold, italic, true, link);
            } else if (child instanceof Code code) {
                var run = text(paragraph, code.getText().toString(), bold, italic, strike, link);
                run.setFontFamily("Courier New");
                run.setFontSize(10);
            } else if (child instanceof Link target) {
                String url = links.apply(target.getUrl().toString());
                inline(paragraph, child, bold, italic, strike, safeLink(url) ? url : null);
            } else if (child instanceof Image image) {
                image(paragraph, image.getUrl().toString(), image.getText().toString());
            } else if (child instanceof ImageRef image) {
                Reference reference = image.getReferenceNode(child.getDocument());
                image(paragraph, reference == null ? "" : reference.getUrl().toString(), image.getText().toString());
            } else if (child instanceof LinkRef ref) {
                Reference reference = ref.getReferenceNode(child.getDocument());
                String url = reference == null ? null : links.apply(reference.getUrl().toString());
                inline(paragraph, child, bold, italic, strike, safeLink(url) ? url : null);
            } else if (child instanceof HtmlInline) {
                html(paragraph, child.getChars().toString());
            } else if (child instanceof HardLineBreak) {
                paragraph.createRun().addBreak();
            } else if (child instanceof SoftLineBreak) {
                text(paragraph, " ", bold, italic, strike, link);
            } else if (child instanceof HtmlEntity) {
                text(paragraph, org.jsoup.parser.Parser.unescapeEntities(child.getChars().toString(), false),
                        bold, italic, strike, link);
            } else {
                inline(paragraph, child, bold, italic, strike, link);
            }
        }
    }

    private void image(XWPFParagraph paragraph, String url, String alt) {
        ExportImage image = images.apply(url);
        if (image == null) {
            paragraph.createRun().setText("[Изображение недоступно" + (alt.isBlank() ? "" : ": " + alt) + "]");
            return;
        }
        double maximumWidth = 560;
        if (paragraph.getBody() instanceof XWPFTableCell cell) {
            int columns = cell.getTableRow().getTableCells().size();
            maximumWidth = Math.max(1, maximumWidth / Math.max(1, columns) - 16);
        }
        double scale = Math.min(1, Math.min(maximumWidth / image.width(), 720.0 / image.height()));
        try (var input = new ByteArrayInputStream(image.bytes())) {
            XWPFPicture picture = paragraph.createRun().addPicture(input,
                    image.mediaType().equals("image/png") ? org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG
                            : org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG,
                    "document-image", org.apache.poi.util.Units.pixelToEMU(Math.max(1, (int) (image.width() * scale))),
                    org.apache.poi.util.Units.pixelToEMU(Math.max(1, (int) (image.height() * scale))));
            picture.getCTPicture().getNvPicPr().getCNvPr().setDescr(alt);
        } catch (java.io.IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
            throw new ExportException(ExportException.Code.ATTACHMENT_UNAVAILABLE);
        }
    }

    /** Raw HTML images pass the same sanitizer before resource resolution. */
    private void html(XWPFParagraph paragraph, String raw) {
        String safe = org.jsoup.Jsoup.clean(raw, "https://knowledge-base.invalid/",
                org.jsoup.safety.Safelist.relaxed().preserveRelativeLinks(true));
        htmlNodes(paragraph, org.jsoup.Jsoup.parseBodyFragment(safe).body(), false, false, null);
    }

    private void htmlNodes(XWPFParagraph paragraph, org.jsoup.nodes.Node node, boolean bold, boolean italic, String link) {
        for (org.jsoup.nodes.Node child : node.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode value) text(paragraph, value.text(), bold, italic, false, link);
            else if (child instanceof org.jsoup.nodes.Element element) {
                if (element.tagName().equals("img")) image(paragraph, element.attr("src"), element.attr("alt"));
                else if (element.tagName().equals("br")) paragraph.createRun().addBreak();
                else {
                    String target = element.tagName().equals("a") ? links.apply(element.attr("href")) : link;
                    htmlNodes(paragraph, element, bold || element.tagName().equals("strong"),
                            italic || element.tagName().equals("em"), safeLink(target) ? target : null);
                }
            }
        }
    }

    private boolean safeLink(String url) {
        if (url == null) return false;
        try {
            var uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            return scheme == null || List.of("http", "https", "mailto").contains(scheme.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private XWPFRun text(XWPFParagraph paragraph, String value, boolean bold, boolean italic,
                         boolean strike, String link) {
        XWPFRun run = link == null ? paragraph.createRun() : paragraph.createHyperlinkRun(link);
        // Leave false properties unset so paragraph styles (especially headings) can inherit.
        if (bold) run.setBold(true);
        if (italic) run.setItalic(true);
        if (strike) run.setStrikeThrough(true);
        if (link != null) {
            run.setColor("0563C1");
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
        run.setText(value);
        return run;
    }
}
