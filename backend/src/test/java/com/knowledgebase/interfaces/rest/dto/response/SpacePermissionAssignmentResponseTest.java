package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты фабричного метода {@link SpacePermissionAssignmentResponse#from}.
 */
class SpacePermissionAssignmentResponseTest {

    @Test
    void shouldMapAllFieldsFromDomain() {
        LocalDateTime granted = LocalDateTime.of(2024, 1, 1, 12, 0);
        SpacePermission permission = SpacePermission.restore(
                42L, 1L, 10L, PermissionType.WRITE, granted);
        User user = User.restore(
                10L, "ivan", "hash", "ivan@example.com",
                GlobalRole.EDITOR, false, false,
                LocalDateTime.now(), LocalDateTime.now());

        SpacePermissionAssignmentResponse response = SpacePermissionAssignmentResponse.from(permission, user);

        assertThat(response.permissionId()).isEqualTo(42L);
        assertThat(response.spaceId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.userLogin()).isEqualTo("ivan");
        assertThat(response.userEmail()).isEqualTo("ivan@example.com");
        assertThat(response.permissionType()).isEqualTo(PermissionType.WRITE);
        assertThat(response.grantedAt()).isEqualTo(granted);
    }

    @Test
    void shouldReturnNullForNullPermission() {
        SpacePermissionAssignmentResponse response = SpacePermissionAssignmentResponse.from(null, null);

        assertThat(response).isNull();
    }

    @Test
    void shouldHandleNullUserGracefully() {
        SpacePermission permission = SpacePermission.restore(
                42L, 1L, 10L, PermissionType.READ, LocalDateTime.now());

        SpacePermissionAssignmentResponse response = SpacePermissionAssignmentResponse.from(permission, null);

        assertThat(response).isNotNull();
        assertThat(response.permissionId()).isEqualTo(42L);
        assertThat(response.userLogin()).isNull();
        assertThat(response.userEmail()).isNull();
        assertThat(response.permissionType()).isEqualTo(PermissionType.READ);
    }
}
