package com.knowledgebase.application.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownPdfFontTest {
    @Test
    void embedsPortableFontsAndPreservesCyrillicInEveryTextStyle() throws Exception {
        byte[] bytes = new MarkdownService().toPdf("База знаний: отчёт", """
                ## Русский заголовок

                Обычный текст и English. **Жирный текст**, *Курсивный текст*, ***Жирный курсив***.

                - Первый пункт
                - Второй пункт

                | Название | Значение |
                | --- | --- |
                | Проверка | Успешно |

                `Код внутри строки`

                ```java
                // Комментарий на русском
                String ответ = "Готово";
                ```
                """);
        Path sample = Path.of("target/pdf-qa/cyrillic.pdf");
        Files.createDirectories(sample.getParent());
        Files.write(sample, bytes);
        try (PDDocument pdf = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            for (String expected : new String[]{"База знаний: отчёт", "Русский заголовок", "Обычный текст",
                    "Жирный текст", "Курсивный текст", "Жирный курсив", "Первый пункт", "Второй пункт",
                    "Название", "Значение", "Проверка", "Успешно", "Код внутри строки",
                    "Комментарий на русском", "Готово"}) {
                assertTrue(text.contains(expected), () -> "Missing: " + expected + " in " + text);
            }
            Set<String> fonts = new HashSet<>();
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    var font = page.getResources().getFont(name);
                    assertTrue(font.isEmbedded(), () -> "Unembedded font: " + font.getName());
                    fonts.add(font.getName());
                }
            }
            for (String expected : new String[]{"NotoSans-Regular", "NotoSans-Bold", "NotoSans-Italic",
                    "NotoSans-BoldItalic", "NotoSansMono-Regular"}) {
                assertTrue(fonts.stream().anyMatch(f -> f.endsWith(expected)), () -> "Missing font " + expected + ": " + fonts);
            }
        }
    }
}
