package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.notification.EmailMessage;
import com.knowledgebase.domain.notification.EmailSender;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты подсистемы email-уведомлений (US4.3.1 / US4.3.2).
 *
 * Реальный {@link EmailSender} подменяется записывающей заглушкой
 * {@link RecordingEmailSender} (бин помечен {@link Primary}). Это позволяет
 * проверять, что нужное письмо сформировано, не отправляя его по SMTP.
 *
 * Слушатели событий срабатывают в фазе AFTER_COMMIT, а тесты не обёрнуты
 * в транзакцию — поэтому сервисные операции фиксируются, и письма
 * записываются синхронно к моменту возврата из MockMvc.
 */
@Import(NotificationIntegrationTest.RecordingMailConfig.class)
class NotificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RecordingEmailSender recordingEmailSender;

    @BeforeEach
    void clearRecordedEmails() {
        recordingEmailSender.clear();
    }

    @Test
    void createUser_dispatchesWelcomeEmail() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("newbie");
        String email = login + "@example.com";

        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"READER","isAdmin":false}
                                """.formatted(login, email)))
                .andExpect(status().isCreated());

        EmailMessage welcome = recordingEmailSender.findByRecipient(email);
        assertThat("приветственное письмо должно быть отправлено новому пользователю",
                welcome, notNullValue());
        assertThat(welcome.subject(), containsStringIgnoringCase("Добро пожаловать"));
        assertThat(welcome.body(), containsString(login));
    }

    @Test
    void grantPermission_dispatchesEmailToUser() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User user = persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String spaceName = "space-" + uniqueLogin("kb");
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(user.getId())))
                .andExpect(status().isCreated());

        EmailMessage notice = recordingEmailSender.findByRecipient("reader@knowledgebase.local");
        assertThat("пользователь должен получить письмо о выданных правах",
                notice, notNullValue());
        assertThat(notice.subject(), containsStringIgnoringCase("прав"));
        assertThat(notice.body(), containsString("WRITE"));
    }

    @Test
    void testEmail_asAdmin_returns202AndQueues() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(post("/api/admin/notifications/test")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"recipient":"qa@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipient", is("qa@example.com")))
                .andExpect(jsonPath("$.queued", is(true)))
                .andExpect(jsonPath("$.notificationsEnabled", is(false)));

        EmailMessage test = recordingEmailSender.findByRecipient("qa@example.com");
        assertThat(test, notNullValue());
        assertThat(test.subject(), containsStringIgnoringCase("Тестовое письмо"));
    }

    @Test
    void testEmail_withoutRecipient_usesAdminEmail() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(post("/api/admin/notifications/test")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipient", is("admin@knowledgebase.local")))
                .andExpect(jsonPath("$.queued", is(true)));
    }

    @Test
    void testEmail_asNonAdmin_returns403() throws Exception {
        persistUser("plainuser", "user123", "plain@knowledgebase.local", GlobalRole.EDITOR, false);
        String userJwt = loginAndGetJwt("plainuser", "user123");

        mockMvc.perform(post("/api/admin/notifications/test")
                        .cookie(jwtCookie(userJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"recipient":"qa@example.com"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(recordingEmailSender.messages(), is(empty()));
    }

    @Test
    void testEmail_invalidRecipient_returns400() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(post("/api/admin/notifications/test")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"recipient":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Тестовая конфигурация: подменяет {@link EmailSender} записывающей заглушкой.
     */
    @TestConfiguration
    static class RecordingMailConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    /**
     * Записывающая реализация порта {@link EmailSender}.
     * Отправка синхронна и потокобезопасна; письма не уходят наружу.
     */
    static class RecordingEmailSender implements EmailSender {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        List<EmailMessage> messages() {
            return sent;
        }

        void clear() {
            sent.clear();
        }

        EmailMessage findByRecipient(String recipient) {
            return sent.stream()
                    .filter(m -> recipient.equals(m.to()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
