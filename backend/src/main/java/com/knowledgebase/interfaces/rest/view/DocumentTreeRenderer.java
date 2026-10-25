package com.knowledgebase.interfaces.rest.view;

import com.knowledgebase.application.service.DocumentService.DocumentTreeNode;
import com.knowledgebase.domain.model.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Renders already-authorized nodes without a template evaluation for every row. */
@Component
public final class DocumentTreeRenderer {
    private static final String END_LIST = "<li class=\"document-tree-end-drop-zone\" aria-hidden=\"true\"></li></ul>";

    public String render(Long spaceId, List<DocumentTreeNode> nodes, Long currentDocumentId, String contextPath) {
        StringBuilder html = new StringBuilder("<ul class=\"document-tree\">");
        String escapedSpaceId = escape(spaceId);
        String documentUrlPrefix = contextPath + "/spaces/" + spaceId + "/doc/";
        // A stack of sibling iterators preserves preorder without consuming the Java call stack.
        Deque<Iterator<DocumentTreeNode>> levels = new ArrayDeque<>();
        levels.push(nodes.iterator());
        while (!levels.isEmpty()) {
            Iterator<DocumentTreeNode> siblings = levels.peek();
            if (!siblings.hasNext()) {
                html.append(END_LIST);
                levels.pop();
                if (!levels.isEmpty()) html.append("</li>");
                continue;
            }

            DocumentTreeNode node = siblings.next();
            Document document = node.getDocument();
            String url = documentUrlPrefix + UriUtils.encodePathSegment(document.getTitle(), StandardCharsets.UTF_8);
            html.append("<li class=\"document-item\" data-document-id=\"").append(escape(document.getId()))
                    .append("\" data-space-id=\"").append(escapedSpaceId)
                    .append("\" data-parent-id=\"").append(escape(document.getParentDocumentId()))
                    .append("\" data-sort-order=\"").append(escape(document.getSortOrder())).append("\">")
                    .append("<div class=\"document-row\" draggable=\"true\" title=\"Перетащите выше, внутрь или ниже другого документа\">")
                    .append("<a href=\"").append(escape(url)).append('"');
            if (currentDocumentId != null && Objects.equals(currentDocumentId, document.getId())) {
                html.append(" class=\"active\"");
            }
            html.append('>').append(escape(document.getTitle())).append("</a></div>");
            if (node.getChildren().isEmpty()) {
                html.append("</li>");
            } else {
                html.append("<ul class=\"document-tree nested-document-list\">");
                levels.push(node.getChildren().iterator());
            }
        }
        return html.toString();
    }

    private static String escape(Object value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value.toString(), StandardCharsets.UTF_8.name());
    }
}
