package com.knowledgebase.domain.service;

import com.knowledgebase.domain.exception.InvalidPermissionAssignmentException;
import com.knowledgebase.domain.exception.LastOwnerProtectionException;
import com.knowledgebase.domain.exception.PermissionAlreadyGrantedException;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Юнит-тесты политики назначения прав на пространства.
 *
 * Чистый POJO-тест без Spring контекста.
 * Проверяет все бизнес-правила policy в изоляции.
 */
class DefaultSpacePermissionAssignmentPolicyTest {

    private DefaultSpacePermissionAssignmentPolicy policy;
    private User activeUser;
    private User deletedUser;

    @BeforeEach
    void setUp() {
        policy = new DefaultSpacePermissionAssignmentPolicy();
        activeUser = User.restore(
                10L, "ivan", "hash", "ivan@example.com",
                GlobalRole.EDITOR, false, false,
                LocalDateTime.now(), LocalDateTime.now());
        deletedUser = User.restore(
                11L, "deleted", "hash", "deleted@example.com",
                GlobalRole.READER, false, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("validateGrant")
    class ValidateGrant {

        @Test
        void shouldPassWhenUserIsActiveAndPermissionNotExists() {
            assertThatCode(() -> policy.validateGrant(activeUser, 1L, PermissionType.READ, false))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWhenUserIsDeleted() {
            assertThatThrownBy(() -> policy.validateGrant(deletedUser, 1L, PermissionType.READ, false))
                    .isInstanceOf(InvalidPermissionAssignmentException.class)
                    .hasMessageContaining("удалённому");
        }

        @Test
        void shouldThrowWhenPermissionAlreadyExists() {
            assertThatThrownBy(() -> policy.validateGrant(activeUser, 1L, PermissionType.WRITE, true))
                    .isInstanceOf(PermissionAlreadyGrantedException.class);
        }

        @Test
        void shouldPrioritizeDeletedCheckOverDuplicateCheck() {
            // Даже если право уже есть, удалённый пользователь — более приоритетная ошибка
            assertThatThrownBy(() -> policy.validateGrant(deletedUser, 1L, PermissionType.READ, true))
                    .isInstanceOf(InvalidPermissionAssignmentException.class);
        }
    }

    @Nested
    @DisplayName("validateRevoke")
    class ValidateRevoke {

        @Test
        void shouldPassWhenRevokingNonOwnerPermission() {
            SpacePermission readPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.READ, LocalDateTime.now());

            assertThatCode(() -> policy.validateRevoke(readPermission, 0))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldPassWhenRevokingOwnerButMoreOwnersExist() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatCode(() -> policy.validateRevoke(ownerPermission, 2))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWhenRevokingLastOwner() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateRevoke(ownerPermission, 1))
                    .isInstanceOf(LastOwnerProtectionException.class)
                    .hasMessageContaining("1");
        }

        @Test
        void shouldThrowWhenOwnerCountIsZero() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateRevoke(ownerPermission, 0))
                    .isInstanceOf(LastOwnerProtectionException.class);
        }
    }

    @Nested
    @DisplayName("validateChange")
    class ValidateChange {

        @Test
        void shouldPassWhenChangingReadToWrite() {
            SpacePermission current = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.READ, LocalDateTime.now());

            assertThatCode(() -> policy.validateChange(current, PermissionType.WRITE, 1, false))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowWhenNewTypeSameAsCurrent() {
            SpacePermission current = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.WRITE, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateChange(current, PermissionType.WRITE, 1, false))
                    .isInstanceOf(InvalidPermissionAssignmentException.class)
                    .hasMessageContaining("совпадает");
        }

        @Test
        void shouldThrowWhenNewTypeAlreadyAssignedToUser() {
            SpacePermission current = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.READ, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateChange(current, PermissionType.WRITE, 1, true))
                    .isInstanceOf(PermissionAlreadyGrantedException.class);
        }

        @Test
        void shouldThrowWhenDemoteLastOwnerToRead() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateChange(ownerPermission, PermissionType.READ, 1, false))
                    .isInstanceOf(LastOwnerProtectionException.class);
        }

        @Test
        void shouldThrowWhenDemoteLastOwnerToWrite() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatThrownBy(() -> policy.validateChange(ownerPermission, PermissionType.WRITE, 1, false))
                    .isInstanceOf(LastOwnerProtectionException.class);
        }

        @Test
        void shouldPassWhenDemoteOwnerButOtherOwnersExist() {
            SpacePermission ownerPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.OWNER, LocalDateTime.now());

            assertThatCode(() -> policy.validateChange(ownerPermission, PermissionType.WRITE, 3, false))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldPassWhenPromoteToOwnerEvenIfLastOwner() {
            // Когда current не OWNER, защита последнего OWNER неактивна
            SpacePermission readPermission = SpacePermission.restore(
                    1L, 1L, 10L, PermissionType.READ, LocalDateTime.now());

            assertThatCode(() -> policy.validateChange(readPermission, PermissionType.OWNER, 1, false))
                    .doesNotThrowAnyException();
        }
    }
}
