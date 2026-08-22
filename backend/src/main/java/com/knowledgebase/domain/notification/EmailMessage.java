package com.knowledgebase.domain.notification;

/**
 * Доменный объект-значение для одного исходящего письма (Domain Layer).
 *
 * Не зависит от Spring/JavaMail — описывает только содержание сообщения.
 * Отправку выполняет порт {@link EmailSender}, реализуемый в infrastructure.
 *
 * @param to      адрес получателя (обязателен)
 * @param subject тема письма
 * @param body    текст письма (plain text)
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email recipient (to) must not be blank");
        }
        if (subject == null) {
            subject = "";
        }
        if (body == null) {
            body = "";
        }
    }
}
