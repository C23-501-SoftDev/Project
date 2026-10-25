package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.domain.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RagPageController {
    private final SpaceService spaceService;

    public RagPageController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping("/ask")
    public String ask(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pageTitle", "Вопрос по базе знаний");
        model.addAttribute("currentUser", user);
        model.addAttribute("spaces", spaceService.getSpacesForUser(user.getId(), user.isAdmin()));
        model.addAttribute("content", "rag/ask");
        return "layout";
    }
}
