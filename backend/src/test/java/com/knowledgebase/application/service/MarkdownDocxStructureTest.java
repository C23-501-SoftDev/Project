package com.knowledgebase.application.service;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownDocxStructureTest {
    private XWPFDocument export(String markdown) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(new MarkdownService().toDocx("Отчёт", markdown)));
    }

    @Test
    void preservesRealHeadingStylesAndEditableTableCells() throws Exception {
        try (var doc = export("""
                ## Раздел

                | Название | Значение |
                | :--- | ---: |
                | **Проверка** | 42 |
                | Строка | *Готово* |
                """)) {
            assertEquals("Heading1", doc.getParagraphs().get(0).getStyle());
            assertEquals("Heading2", doc.getParagraphs().get(1).getStyle());
            var heading = doc.getStyles().getStyle("Heading2").getCTStyle();
            assertEquals(BigInteger.ONE, heading.getPPr().getOutlineLvl().getVal());
            assertEquals(1, heading.getRPr().sizeOfBArray());
            assertEquals(0, doc.getParagraphs().get(1).getRuns().get(0).getCTR().getRPr() == null ? 0
                    : doc.getParagraphs().get(1).getRuns().get(0).getCTR().getRPr().sizeOfBArray());
            assertEquals(1, doc.getTables().size());
            var table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            assertEquals("Название", table.getRow(0).getCell(0).getText());
            assertEquals("Проверка", table.getRow(1).getCell(0).getText());
            assertEquals("Готово", table.getRow(2).getCell(1).getText());
            assertEquals(ParagraphAlignment.RIGHT, table.getRow(1).getCell(1).getParagraphs().get(0).getAlignment());
            assertTrue(table.getRow(1).getCell(0).getParagraphs().get(0).getRuns().get(0).isBold());
            assertTrue(table.getRow(2).getCell(1).getParagraphs().get(0).getRuns().get(0).isItalic());
        }
    }

    @Test
    void retainsNestedListsStartNumbersAndSeparateListRestarts() throws Exception {
        try (var doc = export("""
                3. Третий
                   - Вложенный
                     1. Глубокий
                4. Четвёртый

                Разделитель

                1. Сначала
                """)) {
            var lists = doc.getParagraphs().stream().filter(p -> p.getNumID() != null).toList();
            assertEquals(List.of("Третий", "Вложенный", "Глубокий", "Четвёртый", "Сначала"),
                    lists.stream().map(XWPFParagraph::getText).toList());
            assertEquals(List.of(0, 1, 2, 0, 0), lists.stream().map(p -> p.getNumIlvl().intValue()).toList());
            assertEquals(lists.get(0).getNumID(), lists.get(3).getNumID());
            assertNotEquals(lists.get(0).getNumID(), lists.get(4).getNumID());
            assertEquals(BigInteger.valueOf(3), lists.get(0).getNumStartOverride() == null
                    ? definition(doc, lists.get(0)).getStart().getVal() : lists.get(0).getNumStartOverride());
            assertEquals(BigInteger.ONE, definition(doc, lists.get(4)).getStart().getVal());
            assertEquals("bullet", definition(doc, lists.get(1)).getNumFmt().getVal().toString());
        }
    }

    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl definition(XWPFDocument doc, XWPFParagraph p) {
        var num = doc.getNumbering().getNum(p.getNumID());
        return doc.getNumbering().getAbstractNum(num.getCTNum().getAbstractNumId().getVal())
                .getCTAbstractNum().getLvlArray(0);
    }

    @Test
    void preservesSafeLinksInlineFormattingAndCodeLineBreaks() throws Exception {
        try (var doc = export("""
                **Жирный** *Курсив* ~~Удалено~~ [Ссылка](https://example.org/docs)

                [Опасно](javascript:alert%281%29)

                ```java
                // Русский комментарий
                String ответ = "Готово";
                ```
                """)) {
            var runs = doc.getParagraphs().get(1).getRuns();
            assertTrue(runs.stream().anyMatch(r -> r.isBold() && r.text().equals("Жирный")));
            assertTrue(runs.stream().anyMatch(r -> r.isItalic() && r.text().equals("Курсив")));
            assertTrue(runs.stream().anyMatch(r -> r.isStrikeThrough() && r.text().equals("Удалено")));
            var links = runs.stream().filter(XWPFHyperlinkRun.class::isInstance).map(XWPFHyperlinkRun.class::cast).toList();
            assertEquals(1, links.size());
            assertEquals("https://example.org/docs", links.get(0).getHyperlink(doc).getURL());
            assertTrue(doc.getParagraphs().get(2).getRuns().stream().noneMatch(XWPFHyperlinkRun.class::isInstance));
            var code = doc.getParagraphs().get(3);
            assertEquals("// Русский комментарий\nString ответ = \"Готово\";", code.getText());
            assertEquals(1, code.getRuns().get(0).getCTR().sizeOfBrArray());
        }
    }
}
