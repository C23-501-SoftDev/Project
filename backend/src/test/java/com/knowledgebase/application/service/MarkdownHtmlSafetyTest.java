package com.knowledgebase.application.service;

import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownHtmlSafetyTest {
    private final MarkdownService service = new MarkdownService();

    @Test
    void preservesMarkdownStructureAndRelativeAttachmentsWhileRemovingExecutableMarkup() {
        String html = service.toHtml("""
                # Heading

                **bold** and *italic* and ~~removed~~

                - first
                - second

                | Name | Value |
                | --- | --- |
                | Row | 42 |

                [attachment](/api/attachments/7/download)

                <img src="/api/attachments/8/download" onerror="alert(1)" alt="diagram" />
                <script>alert(1)</script><iframe src="https://example.invalid"></iframe>
                <a href="jav&#x61;script:alert(1)" onclick="alert(1)">unsafe</a>
                <p style="background:url(javascript:alert(1))">ordinary text</p>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertEquals("Heading", doc.selectFirst("h1").text());
        assertEquals(2, doc.select("li").size());
        assertEquals("Row 42", doc.selectFirst("tbody tr").text());
        assertEquals("bold", doc.selectFirst("strong").text());
        assertEquals("removed", doc.selectFirst("del").text());
        assertEquals("/api/attachments/7/download", doc.selectFirst("a[href]").attr("href"));
        assertEquals("/api/attachments/8/download", doc.selectFirst("img").attr("src"));
        assertTrue(doc.select("script,iframe,[onerror],[onclick],[style]").isEmpty());
        assertTrue(doc.select("a[href]").stream().noneMatch(a -> a.attr("href").startsWith("javascript:")));
        assertTrue(doc.text().contains("ordinary text"));
    }

    @Test
    void standaloneHtmlHasTrustedStylesAndEscapedTitle() {
        String title = "Report <script>window.pwned=1</script> & notes";
        var doc = Jsoup.parse(service.toHtmlDocument(title, "## Section\n\n- item"));
        assertEquals(title, doc.title());
        assertEquals(title, doc.selectFirst("h1").text());
        assertEquals("UTF-8", doc.selectFirst("meta[charset]").attr("charset"));
        assertFalse(doc.selectFirst("style").data().isBlank());
        assertEquals("Section", doc.selectFirst("h2").text());
        assertTrue(doc.select("script").isEmpty());
    }

    @Test
    void pdfDoesNotFetchResourcesFromMarkdown() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.png", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            byte[] pdf = service.toPdf("Safe", "Paragraph &nbsp; text\n\n![external](http://127.0.0.1:"
                    + server.getAddress().getPort() + "/image.png)");
            assertTrue(pdf.length > 100);
            assertEquals(0, requests.get(), "PDF conversion must not contact user-supplied URLs");
        } finally {
            server.stop(0);
        }
    }
}
