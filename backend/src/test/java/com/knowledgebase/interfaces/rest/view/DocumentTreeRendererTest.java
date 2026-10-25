package com.knowledgebase.interfaces.rest.view;

import com.knowledgebase.application.service.DocumentService.DocumentTreeNode;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTreeRendererTest {
    private final DocumentTreeRenderer renderer = new DocumentTreeRenderer();

    @Test
    void preservesHierarchySiblingOrderDragTargetsAndCurrentDocument() {
        var child = node(3, "Child", 2L, 8, List.of());
        var parent = node(2, "Parent", null, 5, List.of(child));
        var sibling = node(1, "Sibling", null, 9, List.of());
        var html = Jsoup.parseBodyFragment(renderer.render(7L, List.of(parent, sibling), 3L, ""));

        assertEquals(List.of("2", "3", "1"), html.select(".document-item").eachAttr("data-document-id"));
        var root = html.body().child(0);
        assertEquals("document-tree", root.className());
        assertEquals(List.of("2", "1"), root.children().stream()
                .filter(element -> element.hasClass("document-item")).map(element -> element.attr("data-document-id")).toList());
        var nested = html.selectFirst("[data-document-id=2] > ul.nested-document-list");
        assertNotNull(nested);
        assertEquals("3", nested.child(0).attr("data-document-id"));
        assertEquals("2", nested.child(0).attr("data-parent-id"));
        assertEquals("8", nested.child(0).attr("data-sort-order"));
        assertEquals(List.of("7", "7", "7"), html.select(".document-item").eachAttr("data-space-id"));
        assertEquals(3, html.select(".document-row[draggable=true]").size());
        assertEquals("Child", html.select("a.active").text());
        assertEquals(1, html.select("a.active").size());
        for (var list : html.select("ul.document-tree")) {
            assertTrue(list.lastElementChild().hasClass("document-tree-end-drop-zone"));
            assertEquals("true", list.lastElementChild().attr("aria-hidden"));
        }
    }

    @Test
    void escapesMarkupAndKeepsUnicodeAndReservedCharactersInsideOnePathSegment() {
        String title = "Книга + ' \" <img src=x onerror=alert(1)> & / ? # %";
        String result = renderer.render(7L, List.of(node(1, title, null, 0, List.of())), null, "/knowledge");
        var html = Jsoup.parseBodyFragment(result);
        assertEquals(1, html.select("a").size());
        assertEquals(title, html.selectFirst("a").text());
        assertTrue(html.select("img, script, [onerror], [onclick]").isEmpty());
        assertTrue(result.contains("&lt;img"));
        assertTrue(result.contains("&quot;"));
        assertTrue(html.select("a.active").isEmpty());
        String href = html.selectFirst("a").attr("href");
        var uri = URI.create(href);
        assertEquals("/knowledge/spaces/7/doc/" + title, uri.getPath());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
        assertTrue(href.contains("%2F"));
        assertTrue(href.contains("%3F"));
        assertTrue(href.contains("%23"));
        assertTrue(href.contains("%25"));
        assertFalse(href.contains("Книга"));
    }

    @Test
    void emptyTreeRetainsTheRootDropTarget() {
        var html = Jsoup.parseBodyFragment(renderer.render(7L, List.of(), null, ""));
        assertEquals(1, html.select("ul.document-tree").size());
        assertEquals(1, html.select(".document-tree-end-drop-zone").size());
        assertTrue(html.select(".document-item").isEmpty());
    }

    @Test
    void deepHierarchyDoesNotUseTheJavaCallStack() {
        List<DocumentTreeNode> nodes = List.of();
        for (int id = 10_000; id >= 1; id--) {
            nodes = List.of(node(id, "Depth " + id, id == 1 ? null : (long) id - 1, 0, nodes));
        }
        final List<DocumentTreeNode> deepTree = nodes;
        String result = assertDoesNotThrow(() -> renderer.render(7L, deepTree, 10_000L, ""));
        assertTrue(result.contains("class=\"active\">Depth 10000</a>"));
        assertEquals(10_000, result.split("class=\"document-item\"", -1).length - 1);
        assertEquals(10_000, result.split("</ul>", -1).length - 1);
    }

    private DocumentTreeNode node(long id, String title, Long parentId, int order, List<DocumentTreeNode> children) {
        return new DocumentTreeNode(Document.restore(id, title, "test/" + id + ".md", DocumentStatus.PUBLISHED,
                null, 4L, 7L, null, parentId, null, order, null, null), children);
    }
}
