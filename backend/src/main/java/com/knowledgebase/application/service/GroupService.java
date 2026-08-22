package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.GroupNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.GroupMember;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.GroupMemberRepository;
import com.knowledgebase.domain.repository.SpaceGroupPermissionRepository;
import com.knowledgebase.domain.repository.UserGroupRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления группами пользователей (Application Layer) — US4.1.8 / US4.1.9.
 *
 * Use cases:
 * - CRUD групп (создание с уникальным названием, обновление, удаление);
 * - управление членством (добавление/удаление пользователей);
 * - при удалении группы отзываются все её права на пространства
 *   и удаляются все членства (критерий приёмки US4.1.8, сценарий 2).
 *
 * Все операции журналируются через {@link AuditService} (US4.1.5).
 */
@Service
@Transactional(readOnly = true)
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final UserGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final SpaceGroupPermissionRepository groupPermissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public GroupService(UserGroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        SpaceGroupPermissionRepository groupPermissionRepository,
                        UserRepository userRepository,
                        AuditService auditService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.groupPermissionRepository = groupPermissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Создаёт новую группу.
     *
     * @throws ConflictException если название уже занято
     */
    @Transactional
    public UserGroup createGroup(String name, String description) {
        log.debug("Создание группы: name={}", name);

        if (groupRepository.existsByName(name)) {
            throw new ConflictException("Группа с названием '" + name + "' уже существует");
        }

        UserGroup group = UserGroup.create(name, description);
        UserGroup saved = groupRepository.save(group);

        auditService.record("GROUP_CREATED", AuditService.RESOURCE_GROUP, saved.getId(),
                "name='" + saved.getName() + "'");
        log.info("Группа создана: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Обновляет название и описание группы.
     *
     * @throws GroupNotFoundException если группа не найдена
     * @throws ConflictException      если новое название занято другой группой
     */
    @Transactional
    public UserGroup updateGroup(Long groupId, String name, String description) {
        UserGroup group = getGroupById(groupId);

        if (!group.getName().equals(name) && groupRepository.existsByNameAndIdNot(name, groupId)) {
            throw new ConflictException("Группа с названием '" + name + "' уже существует");
        }

        String oldName = group.getName();
        group.update(name, description);
        UserGroup saved = groupRepository.save(group);

        auditService.record("GROUP_UPDATED", AuditService.RESOURCE_GROUP, groupId,
                "name: '" + oldName + "' -> '" + saved.getName() + "'");
        return saved;
    }

    /**
     * Удаляет группу.
     * Вместе с группой удаляются все членства и отзываются все её права
     * на пространства (US4.1.8, сценарий 2).
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        UserGroup group = getGroupById(groupId);

        // Отзываем права группы и удаляем членства (в PostgreSQL это дублирует FK CASCADE,
        // но выполняется явно — поведение одинаково в любых БД, включая H2 в тестах).
        groupPermissionRepository.deleteByGroupId(groupId);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.deleteById(groupId);

        auditService.record("GROUP_DELETED", AuditService.RESOURCE_GROUP, groupId,
                "name='" + group.getName() + "'");
        log.info("Группа удалена: id={}, name={}", groupId, group.getName());
    }

    /**
     * Возвращает группу по ID.
     *
     * @throws GroupNotFoundException если не найдена
     */
    public UserGroup getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    /**
     * Возвращает страницу групп (сортировка по названию).
     */
    public List<UserGroup> getGroups(int page, int size) {
        return groupRepository.findAll(page, size);
    }

    /**
     * Возвращает общее количество групп.
     */
    public long countGroups() {
        return groupRepository.count();
    }

    /**
     * Возвращает количество участников группы.
     */
    public long countMembers(Long groupId) {
        return memberRepository.countByGroupId(groupId);
    }

    // ── Членство (US4.1.9) ──────────────────────────────────────────────────

    /**
     * Добавляет пользователя в группу.
     *
     * @throws GroupNotFoundException если группа не найдена
     * @throws UserNotFoundException  если пользователь не найден
     * @throws ConflictException      если пользователь уже состоит в группе
     */
    @Transactional
    public GroupMember addMember(Long groupId, Long userId) {
        getGroupById(groupId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("Пользователь уже состоит в этой группе");
        }

        GroupMember saved = memberRepository.save(GroupMember.create(groupId, userId));

        auditService.record("GROUP_MEMBER_ADDED", AuditService.RESOURCE_GROUP, groupId,
                "userId=" + userId + ", login='" + user.getLogin() + "'");
        log.info("Пользователь {} добавлен в группу {}", userId, groupId);
        return saved;
    }

    /**
     * Удаляет пользователя из группы.
     * Пользователь теряет права на пространства, полученные через эту группу (US4.1.9, сценарий 2).
     *
     * @throws GroupNotFoundException если группа не найдена
     * @throws ConflictException      если пользователь не состоит в группе
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        getGroupById(groupId);

        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("Пользователь не состоит в этой группе");
        }

        memberRepository.deleteByGroupIdAndUserId(groupId, userId);

        auditService.record("GROUP_MEMBER_REMOVED", AuditService.RESOURCE_GROUP, groupId,
                "userId=" + userId);
        log.info("Пользователь {} удалён из группы {}", userId, groupId);
    }

    /**
     * Возвращает участников группы.
     *
     * @throws GroupNotFoundException если группа не найдена
     */
    public List<GroupMember> getMembers(Long groupId) {
        getGroupById(groupId);
        return memberRepository.findByGroupId(groupId);
    }
}
