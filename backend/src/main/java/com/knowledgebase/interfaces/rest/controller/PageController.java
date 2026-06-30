package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.domain.model.User;
import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.interfaces.rest.dto.response.DocumentResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.stream.Collectors;

/**
 * MVC контроллер для SSR страниц (Thymeleaf).
 * 
 * Возвращает имена шаблонов Thymeleaf для рендеринга на сервере.
 * Данные для шаблонов передаются через Model.
 * 
 * Шаблоны должны быть созданы в src/main/resources/templates/
 */
@Controller
@RequestMapping
public class PageController {
    // Пока что нет загрузки реальных данных, но вообще она должна быть
    // PageController написан для проверки работоспособности MVC и требует доработок
    // После переработки эти комментарии удалить

    private final SpaceRepository spaceRepository;
    private final SpaceService spaceService;
    private final DocumentService documentService;
    private final com.knowledgebase.application.service.PermissionService permissionService;

    public PageController(SpaceRepository spaceRepository, SpaceService spaceService, DocumentService documentService, com.knowledgebase.application.service.PermissionService permissionService) {
        this.spaceRepository = spaceRepository;
        this.spaceService = spaceService;
        this.documentService = documentService;
        this.permissionService = permissionService;
    }

    private void addAllSpacesTrees(Model model, User user) {
        var spaces = spaceService.getSpacesForUser(user.getId(), user.isAdmin(), 0, 100);
        model.addAttribute("spaces", spaces);
        var trees = spaces.stream()
            .collect(Collectors.toMap(
                space -> space.getId(),
                space -> documentService.getSpaceDocumentHierarchy(space.getId())
            ));
        model.addAttribute("spaceTrees", trees);
    }

    private void addSidebarData(Long spaceId, Model model, User user) {
        addAllSpacesTrees(model, user);
        if (spaceId != null) {
            model.addAttribute("currentSpace", spaceRepository.findById(spaceId).orElse(null));
        }
    }

    /**
     * GET /
     * Главная страница — список документов.
     */
    @GetMapping("/")
    public String index(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Главная — База знаний");
        model.addAttribute("currentUser", user);
        addAllSpacesTrees(model, user);
        model.addAttribute("content", "pages/document-list");
        return "layout";
    }

    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Просмотр документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        
        var doc = documentService.getDocumentById(id);
        addSidebarData(doc.getSpaceId(), model, user);
        
        model.addAttribute("content", "pages/document-view");
        return "layout";
    }

    @GetMapping("/documents/new")
    public String newDocument(@RequestParam(required = false) Long spaceId, @AuthenticationPrincipal User user, Model model) {
        // Проверяем, есть ли у пользователя доступ к созданию ХОТЯ БЫ В ОДНОМ пространстве.
        var writableSpaces = spaceService.getSpacesForUser(user.getId(), user.isAdmin(), com.knowledgebase.domain.model.PermissionType.WRITE, 0, 1);
        
        if (writableSpaces.isEmpty()) {
            return "redirect:/";
        }

        // Если spaceId передан, проверяем права на запись в это конкретное пространство
        if (spaceId != null && !permissionService.canWrite(user.getId(), user.isAdmin(), spaceId)) {
            return "redirect:/spaces/" + spaceId;
        }

        model.addAttribute("pageTitle", "Создание документа");
        model.addAttribute("currentUser", user);
        addAllSpacesTrees(model, user);
        model.addAttribute("content", "pages/document-new");
        return "layout";
    }

    @GetMapping("/documents/{id}/edit")
    public String editDocument(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        var doc = documentService.getDocumentById(id);
        
        // Проверка прав на редактирование
        if (!permissionService.canWrite(user.getId(), user.isAdmin(), doc.getSpaceId())) {
            return "redirect:/documents/" + id;
        }

        model.addAttribute("pageTitle", "Редактирование документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        
        addSidebarData(doc.getSpaceId(), model, user);
        
        model.addAttribute("content", "pages/document-edit");
        return "layout";
    }

    @GetMapping("/documents/{id}/history")
    public String documentHistory(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "История версий");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        model.addAttribute("content", "pages/document-history");
        return "layout";
    }

    @GetMapping("/search")
    public String search(@RequestParam String q, @AuthenticationPrincipal User user, Model model) {
        var searchPage = documentService.searchDocumentsByTitle(q, user.getId(), user.isAdmin(), 0, 20);

        model.addAttribute("searchResults", searchPage.getContent().stream()
            .map(document -> new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSpaceId(),
                spaceRepository.findById(document.getSpaceId()).map(space -> space.getName()).orElse(null),
                document.getAuthorId(),
                null,
                document.getStatus(),
                null,
                document.getGitFilePath(),
                document.getCreatedAt(),
                document.getUpdatedAt()))
            .toList());
        model.addAttribute("pageTitle", "Поиск: " + q);
        model.addAttribute("currentUser", user);
        model.addAttribute("searchQuery", q);
        model.addAttribute("searchTotalElements", searchPage.getTotalElements());
        model.addAttribute("content", "pages/search-results");
        return "layout";
    }

    @GetMapping("/spaces/{id}")
    public String viewSpace(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Пространство");
        model.addAttribute("currentUser", user);
        model.addAttribute("spaceId", id);
        
        addSidebarData(id, model, user);
        
        model.addAttribute("content", "pages/space-view");
        return "layout";
    }

    @GetMapping("/spaces/{spaceId}/doc/{docTitle}")
    public String viewDocumentInSpace(@PathVariable Long spaceId, @PathVariable String docTitle, @AuthenticationPrincipal User user, Model model) {
        // Декодируем docTitle, так как он может приходить в URL с закодированными символами
        String decodedTitle = java.net.URLDecoder.decode(docTitle, java.nio.charset.StandardCharsets.UTF_8);
        var doc = documentService.getDocumentBySpaceAndTitle(spaceId, decodedTitle);
        model.addAttribute("pageTitle", "Просмотр документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", doc.getId());
        addSidebarData(spaceId, model, user);
        model.addAttribute("content", "pages/document-view");
        return "layout";
    }

    @GetMapping("/spaces/{spaceId}/doc/{docTitle}/edit")
    public String editDocumentInSpace(@PathVariable Long spaceId, @PathVariable String docTitle, @AuthenticationPrincipal User user, Model model) {
        String decodedTitle = java.net.URLDecoder.decode(docTitle, java.nio.charset.StandardCharsets.UTF_8);
        var doc = documentService.getDocumentBySpaceAndTitle(spaceId, decodedTitle);
        
        // Проверка прав на редактирование
        if (!permissionService.canWrite(user.getId(), user.isAdmin(), spaceId)) {
            return "redirect:/spaces/" + spaceId + "/doc/" + docTitle;
        }

        model.addAttribute("pageTitle", "Редактирование документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", doc.getId());
        addSidebarData(spaceId, model, user);
        model.addAttribute("content", "pages/document-edit");
        return "layout";
    }

    /**
     * GET /admin/users
     * Панель администратора — управление пользователями.
     */
    @GetMapping("/admin/users")
    public String adminUsers(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Управление пользователями");
        model.addAttribute("currentUser", user);
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "pages/admin-users");
        return "admin-layout";
    }

    /**
     * GET /admin/spaces
     * Панель администратора — управление пространствами.
     */
    @GetMapping("/admin/spaces")
    public String adminSpaces(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Управление пространствами");
        model.addAttribute("currentUser", user);
        model.addAttribute("activePage", "spaces");
        model.addAttribute("content", "pages/admin-spaces");
        return "admin-layout";
    }

    /**
     * GET /admin/settings
     * Панель администратора — настройки.
     */
    @GetMapping("/admin/settings")
    public String adminSettings(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Настройки");
        model.addAttribute("currentUser", user);
        model.addAttribute("activePage", "settings");
        model.addAttribute("content", "pages/admin-settings");
        return "admin-layout";
    }
}
