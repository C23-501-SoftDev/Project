package com.knowledgebase.interfaces.rest.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер страниц HTTP-ошибок для браузерных запросов (SSR).
 *
 * Реализует {@link ErrorController} — перехватывает все запросы на /error,
 * которые Spring Boot направляет сюда после возникновения исключения или
 * отправки response.sendError(...).
 *
 * Логика:
 * - Для браузерных запросов (text/html) — рендерит Thymeleaf-шаблон error/error.html
 *   с соответствующим статусом (403, 404, 500 и т.д.)
 * - Для API/AJAX запросов (application/json) — не вмешивается, ответ формируется
 *   через {@link com.knowledgebase.interfaces.rest.advice.GlobalExceptionHandler}
 *
 * Шаблоны: src/main/resources/templates/error/
 */
@Controller
public class ErrorPageController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ErrorPageController.class);

    /**
     * Обрабатывает /error для браузерных запросов (Accept: text/html).
     *
     * Извлекает из атрибутов запроса HTTP-статус, сообщение и путь,
     * передаёт их в модель Thymeleaf для рендеринга страницы ошибки.
     */
    @RequestMapping(value = "/error", produces = MediaType.TEXT_HTML_VALUE)
    public String handleError(HttpServletRequest request, Model model) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object messageAttr = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object pathAttr = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int status = 500;
        if (statusAttr != null) {
            try {
                status = Integer.parseInt(statusAttr.toString());
            } catch (NumberFormatException e) {
                log.warn("Не удалось распарсить HTTP-статус из атрибута запроса: {}", statusAttr);
            }
        }

        String path = pathAttr != null ? pathAttr.toString() : request.getRequestURI();
        String message = messageAttr != null ? messageAttr.toString() : null;

        log.warn("Страница ошибки: status={}, path={}", status, path);

        model.addAttribute("status", status);
        model.addAttribute("error", resolveErrorTitle(status));
        model.addAttribute("message", message);
        model.addAttribute("path", path);

        return "error/error";
    }

    /**
     * Возвращает человекочитаемый заголовок для HTTP-статуса.
     */
    private String resolveErrorTitle(int status) {
        return switch (status) {
            case 400 -> "Некорректный запрос";
            case 401 -> "Требуется авторизация";
            case 403 -> "Доступ запрещён";
            case 404 -> "Страница не найдена";
            case 405 -> "Метод не поддерживается";
            case 408 -> "Время ожидания истекло";
            case 409 -> "Конфликт данных";
            case 422 -> "Ошибка обработки данных";
            case 429 -> "Слишком много запросов";
            case 500 -> "Внутренняя ошибка сервера";
            case 502 -> "Ошибка шлюза";
            case 503 -> "Сервис недоступен";
            case 504 -> "Таймаут шлюза";
            default  -> "Произошла ошибка";
        };
    }
}
