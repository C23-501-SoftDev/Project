package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpaceRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    private final SpaceRepository spaceRepository;

    public PageController(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    /**
     * GET /
     * Главная страница — список документов.
     */
    @GetMapping("/")
    public String index(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Главная — База знаний");
        model.addAttribute("currentUser", user);
        model.addAttribute("content", "pages/document-list");
        return "layout";
    }

    /**
     * GET /documents/{id}
     * Страница просмотра документа.
     */
    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Просмотр документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        model.addAttribute("content", "pages/document-view");
        return "layout";
    }

    /**
     * GET /documents/new
     * Страница создания нового документа.
     */
    @GetMapping("/documents/new")
    public String newDocument(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Создание документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("content", "pages/document-new");
        return "layout";
    }

    /**
     * GET /documents/{id}/edit
     * Страница редактирования документа.
     */
    @GetMapping("/documents/{id}/edit")
    public String editDocument(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Редактирование документа");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        model.addAttribute("content", "pages/document-edit");
        return "layout";
    }

    /**
     * GET /documents/{id}/history
     * Страница истории версий документа.
     */
    @GetMapping("/documents/{id}/history")
    public String documentHistory(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "История версий");
        model.addAttribute("currentUser", user);
        model.addAttribute("documentId", id);
        model.addAttribute("content", "pages/document-history");
        return "layout";
    }

    /**
     * GET /search
     * Страница результатов поиска.
     */
    @GetMapping("/search")
    public String search(@RequestParam String q, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Поиск: " + q);
        model.addAttribute("currentUser", user);
        model.addAttribute("searchQuery", q);
        model.addAttribute("content", "pages/search-results");
        return "layout";
    }

    /**
     * GET /spaces/{id}
     * Страница пространства.
     */
    @GetMapping("/spaces/{id}")
    public String viewSpace(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Пространство");
        model.addAttribute("currentUser", user);
        model.addAttribute("spaceId", id);
        model.addAttribute("content", "pages/space-view");
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
        model.addAttribute("content", "pages/admin-spaces");
        return "admin-layout";
    }
}
