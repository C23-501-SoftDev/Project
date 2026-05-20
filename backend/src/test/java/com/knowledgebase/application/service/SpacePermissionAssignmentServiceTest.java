package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.SpacePermissionGrantedEvent;
import com.knowledgebase.domain.exception.LastOwnerProtectionException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.SpacePermissionNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.domain.service.SpacePermissionAssignmentPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты сервиса назначения прав на пространства.
 *
 * Использует Mockito для изоляции от инфраструктуры.
 * Проверяет логику оркестрации сервиса, делегирование политики,
 * публикацию событий и транзакционные взаимодействия.
 */
@ExtendWith(MockitoExtension.class)
class SpacePermissionAssignmentServiceTest {

    @Mock
    private SpacePermissionRepository permissionRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpacePermissionAssignmentPolicy policy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SpacePermissionAssignmentService service;

    private Space space;
    private User user;
    private SpacePermission readPermission;
    private SpacePermission ownerPermission;

    @BeforeEach
    void setUp() {
        space = Space.create("test-space", "desc", 99L);
        user = User.restore(
                10L, "ivan", "hash", "ivan@example.com",
                GlobalRole.EDITOR, false, false,
                LocalDateTime.now(), LocalDateTime.now());
        readPermission = SpacePermission.restore(
                100L, 1L, 10L, PermissionType.READ, LocalDateTime.now());
        ownerPermission = SpacePermission.restore(
                101L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());
    }

    @Nested
    @DisplayName("assign()")
    class Assign {

        @Test
        void shouldAssignPermissionWhenPolicyApproves() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(1L, 10L, PermissionType.READ))
                    .thenReturn(false);
            when(permissionRepository.save(any(SpacePermission.class))).thenReturn(readPermission);

            SpacePermission result = service.assign(1L, 10L, PermissionType.READ);

            assertThat(result).isEqualTo(readPermission);
            verify(policy).validateGrant(user, 1L, PermissionType.READ, false);
            verify(permissionRepository).save(any(SpacePermission.class));
            verify(eventPublisher).publishEvent(any(SpacePermissionGrantedEvent.class));
        }

        @Test
        void shouldPublishCorrectEvent() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(permissionRepository.save(any(SpacePermission.class))).thenReturn(readPermission);

            service.assign(1L, 10L, PermissionType.WRITE);

            ArgumentCaptor<SpacePermissionGrantedEvent> captor =
                    ArgumentCaptor.forClass(SpacePermissionGrantedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            SpacePermissionGrantedEvent event = captor.getValue();
            assertThat(event.getSpaceId()).isEqualTo(1L);
            assertThat(event.getUserId()).isEqualTo(10L);
            assertThat(event.getPermissionType()).isEqualTo("WRITE");
        }

        @Test
        void shouldThrowWhenSpaceNotFound() {
            when(spaceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assign(999L, 10L, PermissionType.READ))
                    .isInstanceOf(SpaceNotFoundException.class);

            verify(permissionRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assign(1L, 999L, PermissionType.READ))
                    .isInstanceOf(UserNotFoundException.class);

            verify(permissionRepository, never()).save(any());
        }

        @Test
        void shouldNotSaveWhenPolicyRejects() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(1L, 10L, PermissionType.READ))
                    .thenReturn(false);
            org.mockito.Mockito.doThrow(new RuntimeException("policy rejected"))
                    .when(policy).validateGrant(any(), anyLong(), any(), anyBoolean());

            assertThatThrownBy(() -> service.assign(1L, 10L, PermissionType.READ))
                    .isInstanceOf(RuntimeException.class);

            verify(permissionRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("changeType()")
    class ChangeType {

        @Test
        void shouldDeleteOldAndCreateNewPermission() {
            when(permissionRepository.findById(100L)).thenReturn(Optional.of(readPermission));
            when(permissionRepository.findBySpaceId(1L)).thenReturn(List.of(readPermission));
            when(permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(1L, 10L, PermissionType.WRITE))
                    .thenReturn(false);
            SpacePermission writePermission = SpacePermission.restore(
                    102L, 1L, 10L, PermissionType.WRITE, LocalDateTime.now());
            when(permissionRepository.save(any(SpacePermission.class))).thenReturn(writePermission);

            SpacePermission result = service.changeType(1L, 100L, PermissionType.WRITE);

            assertThat(result).isEqualTo(writePermission);
            verify(permissionRepository).deleteById(100L);
            verify(permissionRepository).save(any(SpacePermission.class));
            verify(eventPublisher).publishEvent(any(SpacePermissionGrantedEvent.class));
        }

        @Test
        void shouldThrowWhenPermissionNotFound() {
            when(permissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeType(1L, 999L, PermissionType.WRITE))
                    .isInstanceOf(SpacePermissionNotFoundException.class);
        }

        @Test
        void shouldThrowWhenPermissionDoesNotBelongToSpace() {
            SpacePermission otherSpacePermission = SpacePermission.restore(
                    100L, 999L, 10L, PermissionType.READ, LocalDateTime.now());
            when(permissionRepository.findById(100L)).thenReturn(Optional.of(otherSpacePermission));

            assertThatThrownBy(() -> service.changeType(1L, 100L, PermissionType.WRITE))
                    .isInstanceOf(SpacePermissionNotFoundException.class);

            verify(permissionRepository, never()).deleteById(any());
        }

        @Test
        void shouldCalculateOwnerCountCorrectly() {
            SpacePermission anotherOwner = SpacePermission.restore(
                    103L, 1L, 20L, PermissionType.OWNER, LocalDateTime.now());
            when(permissionRepository.findById(101L)).thenReturn(Optional.of(ownerPermission));
            when(permissionRepository.findBySpaceId(1L))
                    .thenReturn(List.of(ownerPermission, anotherOwner, readPermission));
            when(permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(1L, 10L, PermissionType.WRITE))
                    .thenReturn(false);
            when(permissionRepository.save(any(SpacePermission.class))).thenReturn(readPermission);

            service.changeType(1L, 101L, PermissionType.WRITE);

            // 2 OWNER в пространстве → можно понижать
            verify(policy).validateChange(ownerPermission, PermissionType.WRITE, 2, false);
        }
    }

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        void shouldRevokePermission() {
            when(permissionRepository.findById(100L)).thenReturn(Optional.of(readPermission));
            when(permissionRepository.findBySpaceId(1L)).thenReturn(List.of(readPermission));

            service.revoke(1L, 100L);

            verify(policy).validateRevoke(readPermission, 0);
            verify(permissionRepository).deleteById(100L);
        }

        @Test
        void shouldThrowWhenPermissionNotFound() {
            when(permissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revoke(1L, 999L))
                    .isInstanceOf(SpacePermissionNotFoundException.class);

            verify(permissionRepository, never()).deleteById(any());
        }

        @Test
        void shouldThrowWhenPermissionDoesNotBelongToSpace() {
            SpacePermission otherSpacePermission = SpacePermission.restore(
                    100L, 999L, 10L, PermissionType.READ, LocalDateTime.now());
            when(permissionRepository.findById(100L)).thenReturn(Optional.of(otherSpacePermission));

            assertThatThrownBy(() -> service.revoke(1L, 100L))
                    .isInstanceOf(SpacePermissionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("revokeAllForUser()")
    class RevokeAllForUser {

        @Test
        void shouldRevokeAllUserPermissionsInSpace() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L))
                    .thenReturn(List.of(readPermission));

            service.revokeAllForUser(1L, 10L);

            verify(permissionRepository).deleteBySpaceIdAndUserId(1L, 10L);
        }

        @Test
        void shouldThrowWhenSpaceNotFound() {
            when(spaceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeAllForUser(999L, 10L))
                    .isInstanceOf(SpaceNotFoundException.class);
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeAllForUser(1L, 999L))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void shouldThrowWhenNoPermissionsExist() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.revokeAllForUser(1L, 10L))
                    .isInstanceOf(SpacePermissionNotFoundException.class);

            verify(permissionRepository, never()).deleteBySpaceIdAndUserId(anyLong(), anyLong());
        }

        @Test
        void shouldThrowWhenRevokingLastOwner() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L))
                    .thenReturn(List.of(ownerPermission));
            when(permissionRepository.findBySpaceId(1L))
                    .thenReturn(List.of(ownerPermission));

            assertThatThrownBy(() -> service.revokeAllForUser(1L, 10L))
                    .isInstanceOf(LastOwnerProtectionException.class);

            verify(permissionRepository, never()).deleteBySpaceIdAndUserId(anyLong(), anyLong());
        }

        @Test
        void shouldRevokeOwnerWhenOtherOwnersExist() {
            SpacePermission anotherOwner = SpacePermission.restore(
                    103L, 1L, 20L, PermissionType.OWNER, LocalDateTime.now());
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L))
                    .thenReturn(List.of(ownerPermission));
            when(permissionRepository.findBySpaceId(1L))
                    .thenReturn(List.of(ownerPermission, anotherOwner));

            service.revokeAllForUser(1L, 10L);

            verify(permissionRepository).deleteBySpaceIdAndUserId(1L, 10L);
        }

        @Test
        void shouldRevokeMultiplePermissionsAtOnce() {
            SpacePermission writePermission = SpacePermission.restore(
                    104L, 1L, 10L, PermissionType.WRITE, LocalDateTime.now());
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L))
                    .thenReturn(List.of(readPermission, writePermission));

            service.revokeAllForUser(1L, 10L);

            verify(permissionRepository, times(1)).deleteBySpaceIdAndUserId(1L, 10L);
        }
    }

    @Nested
    @DisplayName("listAssignments() / listAssignmentsForUser()")
    class ListMethods {

        @Test
        void listAssignmentsShouldReturnAllPermissionsForSpace() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(permissionRepository.findBySpaceId(1L))
                    .thenReturn(List.of(readPermission, ownerPermission));

            List<SpacePermission> result = service.listAssignments(1L);

            assertThat(result).hasSize(2).containsExactly(readPermission, ownerPermission);
        }

        @Test
        void listAssignmentsShouldThrowWhenSpaceNotFound() {
            when(spaceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listAssignments(999L))
                    .isInstanceOf(SpaceNotFoundException.class);
        }

        @Test
        void listAssignmentsForUserShouldReturnUserPermissions() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L))
                    .thenReturn(List.of(readPermission));

            List<SpacePermission> result = service.listAssignmentsForUser(1L, 10L);

            assertThat(result).hasSize(1).contains(readPermission);
        }

        @Test
        void listAssignmentsForUserShouldThrowWhenSpaceNotFound() {
            when(spaceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listAssignmentsForUser(999L, 10L))
                    .isInstanceOf(SpaceNotFoundException.class);
        }

        @Test
        void listAssignmentsForUserShouldThrowWhenUserNotFound() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listAssignmentsForUser(1L, 999L))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void listAssignmentsForUserShouldReturnEmptyListWhenNoPermissions() {
            when(spaceRepository.findById(1L)).thenReturn(Optional.of(space));
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(permissionRepository.findBySpaceIdAndUserId(1L, 10L)).thenReturn(List.of());

            List<SpacePermission> result = service.listAssignmentsForUser(1L, 10L);

            assertThat(result).isEmpty();
        }
    }
}
