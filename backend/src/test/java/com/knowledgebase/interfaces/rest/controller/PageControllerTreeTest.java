package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.*;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.interfaces.rest.view.DocumentTreeRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PageControllerTreeTest {
    @Test
    void initialPageDoesNotQueryDocumentTrees() {
        var spaces = mock(SpaceService.class);
        var documents = mock(DocumentService.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.isAdmin()).thenReturn(true);
        when(spaces.getSpacesForUser(7L, true)).thenReturn(List.of());
        var controller = new PageController(mock(SpaceRepository.class), spaces, documents, mock(PermissionService.class), new DocumentTreeRenderer());
        var model = new ExtendedModelMap();
        assertEquals("layout", controller.index(user, model));
        assertTrue(model.containsAttribute("spaces"));
        assertFalse(model.containsAttribute("spaceTrees"));
        verifyNoInteractions(documents);
    }
}
