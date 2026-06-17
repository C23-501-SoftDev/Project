package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.GroupMembershipNotFoundException;
import com.knowledgebase.domain.exception.GroupNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.UserGroupMember;
import com.knowledgebase.domain.repository.UserGroupMemberRepository;
import com.knowledgebase.domain.repository.UserGroupRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления членством пользователей в группах (Application Layer).
 *
 * Реализует use cases US4.1.9 "Управление членством в группах":
 * - Добавление пользователя в группу (пользователь наследует права группы)
 * - Удаление пользователя из группы (пользователь теряет права, полученные через группу)
 * - Получение состава группы
 *
 * Членством управляет только администратор (защита на уровне SecurityConfig и контроллера).
 */
@Service
@Transactional(readOnly = true)
public class UserGroupMembershipService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupMembershipService.class);

    private final UserGroupRepository groupRepository;
    private final UserGroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    public UserGroupMembershipService(UserGroupRepository groupRepository,
                                      UserGroupMemberRepository memberRepository,
                                      UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Добавляет пользователя в группу.
     *
     * @param groupId ID группы
     * @param userId  ID пользователя
     * @return созданная запись о членстве
     * @throws GroupNotFoundException если группа не найдена
     * @throws UserNotFoundException  если пользователь не найден (или удалён)
     * @throws ConflictException      если пользователь уже состоит в группе
     */
    @Transactional
    public UserGroupMember addMember(Long groupId, Long userId) {
        log.debug("Добавление пользователя {} в группу {}", userId, groupId);

        requireGroupExists(groupId);

        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }

        if (memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("Пользователь уже состоит в этой группе");
        }

        UserGroupMember saved = memberRepository.save(UserGroupMember.create(groupId, userId));
        log.info("Пользователь {} добавлен в группу {}", userId, groupId);
        return saved;
    }

    /**
     * Удаляет пользователя из группы.
     *
     * @param groupId ID группы
     * @param userId  ID пользователя
     * @throws GroupNotFoundException           если группа не найдена
     * @throws GroupMembershipNotFoundException если пользователь не состоит в группе
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        log.debug("Удаление пользователя {} из группы {}", userId, groupId);

        requireGroupExists(groupId);

        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new GroupMembershipNotFoundException(groupId, userId);
        }

        memberRepository.deleteByGroupIdAndUserId(groupId, userId);
        log.info("Пользователь {} удалён из группы {}", userId, groupId);
    }

    /**
     * Возвращает состав группы.
     *
     * @param groupId ID группы
     * @return список записей о членстве
     * @throws GroupNotFoundException если группа не найдена
     */
    public List<UserGroupMember> getMembers(Long groupId) {
        requireGroupExists(groupId);
        return memberRepository.findByGroupId(groupId);
    }

    private void requireGroupExists(Long groupId) {
        if (groupRepository.findById(groupId).isEmpty()) {
            throw new GroupNotFoundException(groupId);
        }
    }
}
