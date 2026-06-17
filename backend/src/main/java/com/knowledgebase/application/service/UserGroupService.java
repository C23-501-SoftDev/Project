package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.GroupNotFoundException;
import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.UserGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления группами пользователей (Application Layer).
 *
 * Реализует use cases US4.1.8 "Создание и управление группами":
 * - Создание группы с проверкой уникальности названия
 * - Получение списка групп и группы по ID
 * - Обновление названия/описания группы
 * - Удаление группы
 *
 * Группами управляет только администратор (защита на уровне SecurityConfig и контроллера).
 */
@Service
@Transactional(readOnly = true)
public class UserGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupService.class);

    private final UserGroupRepository groupRepository;

    public UserGroupService(UserGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    /**
     * Создаёт новую группу.
     *
     * @param name        уникальное название группы
     * @param description описание группы (может быть null)
     * @return созданная группа
     * @throws ConflictException если группа с таким именем уже существует
     */
    @Transactional
    public UserGroup createGroup(String name, String description) {
        log.debug("Создание группы: name={}", name);

        if (groupRepository.existsByName(name)) {
            throw new ConflictException("Группа с именем '" + name + "' уже существует");
        }

        UserGroup saved = groupRepository.save(UserGroup.create(name, description));
        log.info("Группа создана: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Возвращает группу по ID.
     *
     * @throws GroupNotFoundException если группа не найдена
     */
    public UserGroup getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    /**
     * Возвращает список групп с пагинацией.
     */
    public List<UserGroup> getAllGroups(int page, int size) {
        return groupRepository.findAll(page, size);
    }

    /**
     * Возвращает общее количество групп (для пагинации).
     */
    public long countGroups() {
        return groupRepository.count();
    }

    /**
     * Обновляет название и описание группы.
     *
     * @param groupId     ID группы
     * @param name        новое название
     * @param description новое описание
     * @return обновлённая группа
     * @throws GroupNotFoundException если группа не найдена
     * @throws ConflictException      если новое имя занято другой группой
     */
    @Transactional
    public UserGroup updateGroup(Long groupId, String name, String description) {
        log.debug("Обновление группы: id={}", groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Проверяем уникальность имени, если оно изменилось
        if (!group.getName().equals(name) && groupRepository.existsByNameAndIdNot(name, groupId)) {
            throw new ConflictException("Группа с именем '" + name + "' уже существует");
        }

        group.update(name, description);
        UserGroup updated = groupRepository.save(group);
        log.info("Группа обновлена: id={}", updated.getId());
        return updated;
    }

    /**
     * Удаляет группу.
     *
     * Связанные записи о составе группы и правах группы на пространства удаляются
     * каскадно на уровне БД (ON DELETE CASCADE, changelog 015/016). Сами пользователи
     * не затрагиваются.
     *
     * @param groupId ID группы
     * @throws GroupNotFoundException если группа не найдена
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        log.info("Удаление группы: id={}", groupId);

        if (groupRepository.findById(groupId).isEmpty()) {
            throw new GroupNotFoundException(groupId);
        }

        groupRepository.deleteById(groupId);
        log.info("Группа удалена: id={}", groupId);
    }
}
