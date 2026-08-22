package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.DocumentUpdatedEvent;
import com.knowledgebase.domain.event.SpacePermissionGrantedEvent;
import com.knowledgebase.domain.event.UserCreatedEvent;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.notification.EmailMessage;
import com.knowledgebase.domain.notification.EmailSender;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Подсистема email-уведомлений (Application Layer) — US4.3.1 / US4.3.2.
 *
 * Подписывается на доменные события и формирует письма пользователям.
 * Слушатели срабатывают <b>после фиксации</b> исходной транзакции
 * ({@link TransactionPhase#AFTER_COMMIT}) — это гарантирует, что уведомление
 * не уйдёт, если бизнес-операция была откатана. Сама отправка делегируется
 * порту {@link EmailSender} и выполняется асинхронно (см. реализацию
 * {@code SpringMailEmailSender}).
 *
 * Поддерживаемые события:
 * <ul>
 *   <li>{@link UserCreatedEvent} — приветственное письмо новому пользователю;</li>
 *   <li>{@link SpacePermissionGrantedEvent} — уведомление о выданных правах;</li>
 *   <li>{@link DocumentUpdatedEvent} — уведомление участникам пространства об изменении документа.</li>
 * </ul>
 *
 * Ошибки отправки не пробрасываются: сбой уведомления не должен ломать
 * основную операцию.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailSender emailSender;
    private final NotificationProperties properties;
    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final SpacePermissionRepository permissionRepository;

    public NotificationService(EmailSender emailSender,
                               NotificationProperties properties,
                               UserRepository userRepository,
                               SpaceRepository spaceRepository,
                               SpacePermissionRepository permissionRepository) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Приветственное письмо при создании пользователя.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onUserCreated(UserCreatedEvent event) {
        String body = "Здравствуйте, " + event.getLogin() + "!\n\n"
                + "Для вас создана учётная запись в системе «База знаний».\n"
                + "Логин: " + event.getLogin() + "\n\n"
                + "Это автоматическое уведомление, отвечать на него не нужно.";
        dispatch(event.getEmail(), "Добро пожаловать в Базу знаний", body, "user_created");
    }

    /**
     * Уведомление пользователя о предоставлении прав на пространство.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onPermissionGranted(SpacePermissionGrantedEvent event) {
        User user = userRepository.findByIdIncludingDeleted(event.getUserId()).orElse(null);
        if (user == null) {
            log.warn("Permission-granted notification skipped: user {} not found", event.getUserId());
            return;
        }
        String spaceName = resolveSpaceName(event.getSpaceId());
        String body = "Здравствуйте, " + user.getLogin() + "!\n\n"
                + "Вам предоставлено право «" + event.getPermissionType() + "» "
                + "в пространстве «" + spaceName + "».\n\n"
                + "Это автоматическое уведомление.";
        dispatch(user.getEmail(), "Изменение прав доступа в Базе знаний", body, "permission_granted");
    }

    /**
     * Уведомление участников пространства об изменении документа.
     * Получатели — пользователи с правами в пространстве, кроме автора изменения.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onDocumentUpdated(DocumentUpdatedEvent event) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        for (SpacePermission permission : permissionRepository.findBySpaceId(event.getSpaceId())) {
            if (!permission.getUserId().equals(event.getEditorId())) {
                recipientIds.add(permission.getUserId());
            }
        }
        if (recipientIds.isEmpty()) {
            return;
        }

        String spaceName = resolveSpaceName(event.getSpaceId());
        String subject = "Документ изменён: " + event.getDocumentTitle();
        String body = "В пространстве «" + spaceName + "» изменён документ «"
                + event.getDocumentTitle() + "».\n"
                + "Автор изменения: " + event.getEditorLogin() + "\n\n"
                + "Это автоматическое уведомление.";

        int sent = 0;
        for (Long userId : recipientIds) {
            User user = userRepository.findById(userId).filter(User::isActive).orElse(null);
            if (user != null) {
                dispatch(user.getEmail(), subject, body, "document_updated");
                sent++;
            }
        }
        log.debug("Document-updated notifications dispatched: documentId={}, recipients={}",
                event.getDocumentId(), sent);
    }

    /**
     * Отправляет тестовое письмо (US4.3.2, сценарий 2).
     * Вызывается администратором через REST-эндпоинт.
     *
     * @param recipient адрес получателя; если пуст — используется
     *                  {@code app.notifications.admin-email}
     * @return результат постановки письма в очередь
     */
    public TestEmailResult sendTestEmail(String recipient) {
        String to = (recipient != null && !recipient.isBlank())
                ? recipient.trim()
                : properties.getAdminEmail();
        String body = "Это тестовое письмо из системы «База знаний».\n"
                + "Если вы его получили, параметры SMTP настроены верно.";
        boolean queued = dispatch(to, "Тестовое письмо — База знаний", body, "test_email");
        return new TestEmailResult(to, queued, properties.isEnabled());
    }

    private String resolveSpaceName(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .map(Space::getName)
                .orElse("#" + spaceId);
    }

    /**
     * Безопасно отправляет письмо: проверяет получателя и гасит любые исключения,
     * чтобы уведомление не ломало основную операцию.
     *
     * @return true, если письмо передано отправителю
     */
    private boolean dispatch(String to, String subject, String body, String action) {
        if (to == null || to.isBlank()) {
            log.warn("Notification skipped: empty recipient (action={})", action);
            return false;
        }
        try {
            emailSender.send(new EmailMessage(to, subject, body));
            return true;
        } catch (RuntimeException ex) {
            log.error("Notification dispatch failed (action={}): {}", action, ex.getMessage());
            return false;
        }
    }

    /**
     * Результат запроса на отправку тестового письма.
     *
     * @param recipient            фактический адрес получателя
     * @param queued               письмо передано отправителю (поставлено в очередь)
     * @param notificationsEnabled включена ли реальная рассылка (app.notifications.enabled)
     */
    public record TestEmailResult(String recipient, boolean queued, boolean notificationsEnabled) {
    }
}
