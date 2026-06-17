package com.knowledgebase.interfaces.rest.mapper;

import com.knowledgebase.application.service.MarkdownService;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.interfaces.rest.dto.response.AttachmentResponse;
import com.knowledgebase.interfaces.rest.dto.response.DocumentResponse;
import com.knowledgebase.interfaces.rest.dto.response.GroupResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpacePermissionResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpaceResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RestDtoMapper {

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final MarkdownService markdownService;

    public RestDtoMapper(UserRepository userRepository,
                         SpaceRepository spaceRepository,
                         MarkdownService markdownService) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.markdownService = markdownService;
    }

    // ── User ──────────────────────────────────────────────────────────────────

    /**
     * Конвертирует доменный User в UserResponse DTO.
     * Никогда не включает passwordHash в ответ!
     */
    public UserResponse toUserResponse(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getRole(),
                user.getIsAdmin(),
                user.getIsDeleted(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    // ── Space ─────────────────────────────────────────────────────────────────

    public SpaceResponse toSpaceResponse(Space space) {
        if (space == null) return null;
        String ownerLogin = getOwnerLogin(space.getOwnerId());
        return new SpaceResponse(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getOwnerId(),
                ownerLogin,
                space.getCreatedAt(),
                space.getUpdatedAt(),
                space.isDeleted() ? "Deleted" : "Active",
                space.isDeleted()
        );
    }

    private String getOwnerLogin(Long ownerId) {
        if (ownerId == null) return null;
        Optional<User> owner = userRepository.findById(ownerId);
        return owner.map(User::getLogin).orElse(null);
    }

    // ── UserGroup ─────────────────────────────────────────────────────────────

    public GroupResponse toGroupResponse(UserGroup group) {
        if (group == null) return null;
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    // ── SpacePermission ───────────────────────────────────────────────────────

    public SpacePermissionResponse toSpacePermissionResponse(SpacePermission permission) {
        if (permission == null) return null;
        String login = null;
        String email = null;
        boolean isDeleted = false;
        Optional<User> user = userRepository.findByIdIncludingDeleted(permission.getUserId());
        if (user.isPresent()) {
            login = user.get().getLogin();
            email = user.get().getEmail();
            isDeleted = user.get().getIsDeleted();
        }
        return new SpacePermissionResponse(
                permission.getId(),
                permission.getSpaceId(),
                permission.getUserId(),
                login,
                email,
                permission.getPermissionType(),
                permission.getGrantedAt(),
                isDeleted
        );
    }

    // ── Document ──────────────────────────────────────────────────────────────

    public DocumentResponse toDocumentResponse(Document document, String content) {
        return toDocumentResponse(document, content, null);
    }

    public DocumentResponse toDocumentResponse(Document document, String content, String contentHtml) {
        if (document == null) return null;

        String spaceName = null;
        if (document.getSpaceId() != null) {
            spaceName = spaceRepository.findById(document.getSpaceId())
                    .map(Space::getName).orElse(null);
        }

        String authorLogin = null;
        if (document.getAuthorId() != null) {
            authorLogin = userRepository.findByIdIncludingDeleted(document.getAuthorId())
                    .map(User::getLogin).orElse(null);
        }

        String resolvedHtml = contentHtml != null ? contentHtml
                : (content != null ? markdownService.toHtml(content) : null);

        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSpaceId(),
                spaceName,
                document.getAuthorId(),
                authorLogin,
                document.getStatus(),
                content,
                resolvedHtml,
                document.getGitFilePath(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    public AttachmentResponse toAttachmentResponse(Attachment attachment) {
        if (attachment == null) {
            return null;
        }

        String uploaderLogin = null;
        if (attachment.getUploadedBy() != null) {
            uploaderLogin = userRepository.findByIdIncludingDeleted(attachment.getUploadedBy())
                    .map(User::getLogin)
                    .orElse(null);
        }

        return new AttachmentResponse(
                attachment.getId(),
                attachment.getDocumentId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy(),
                uploaderLogin,
                attachment.getUploadedAt()
        );
    }
}

