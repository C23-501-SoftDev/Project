package com.knowledgebase.interfaces.rest.mapper;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.interfaces.rest.dto.response.DocumentResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpacePermissionResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpaceResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Маппер DTO ↔ Domain для слоя interfaces.
 *
 * Преобразует доменные объекты в DTO для HTTP-ответов.
 * Написан вручную (без MapStruct) для полного контроля над маппингом.
 */
@Component
public class RestDtoMapper {

    private final UserRepository userRepository;

    public RestDtoMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                space.getUpdatedAt()
        );
    }

    private String getOwnerLogin(Long ownerId) {
        if (ownerId == null) return null;
        Optional<User> owner = userRepository.findById(ownerId);
        return owner.map(User::getLogin).orElse(null);
    }

    // ── SpacePermission ───────────────────────────────────────────────────────

    public SpacePermissionResponse toSpacePermissionResponse(SpacePermission permission) {
        if (permission == null) return null;
        String login = null;
        String email = null;
        Optional<User> user = userRepository.findById(permission.getUserId());
        if (user.isPresent()) {
            login = user.get().getLogin();
            email = user.get().getEmail();
        }
        return new SpacePermissionResponse(
                permission.getId(),
                permission.getSpaceId(),
                permission.getUserId(),
                login,
                email,
                permission.getPermissionType(),
                permission.getGrantedAt()
        );
    }

    // ── Document ──────────────────────────────────────────────────────────────

    public DocumentResponse toDocumentResponse(Document document, String content) {
        if (document == null) return null;
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSpaceId(),
                document.getAuthorId(),
                document.getStatus(),
                content,
                document.getGitFilePath(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
