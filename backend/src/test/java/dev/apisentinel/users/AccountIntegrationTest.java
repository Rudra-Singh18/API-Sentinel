package dev.apisentinel.users;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
        @MockBean private JavaMailSender mailSender;

    @Test
        void profileAndNotificationPreferencesArePersistedPerAuthenticatedAccount() throws Exception {
        MockHttpSession session = registerAccount();

        mvc.perform(get("/api/v1/account").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.incidentEmailsEnabled").value(true))
                .andExpect(jsonPath("$.emailConfigured").value(true));

        mvc.perform(patch("/api/v1/account/profile").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Avery Updated","timeZone":"America/Los_Angeles"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Avery Updated"))
                .andExpect(jsonPath("$.timeZone").value("America/Los_Angeles"));

        mvc.perform(patch("/api/v1/account/notifications").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentEmailsEnabled":false,"recoveryEmailsEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentEmailsEnabled").value(false))
                .andExpect(jsonPath("$.recoveryEmailsEnabled").value(true));

        mvc.perform(get("/api/v1/account").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Avery Updated"))
                .andExpect(jsonPath("$.timeZone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.incidentEmailsEnabled").value(false));

    }

    @Test
    void settingsAndTestMailRequireAuthenticationAndUnavailableMailIsNotReportedAsSent() throws Exception {
        mvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        MockHttpSession session = registerAccount();
        doThrow(new MailSendException("test failure")).when(mailSender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/v1/account/notifications/test").session(session).with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }

    private MockHttpSession registerAccount() throws Exception {
        String email = "settings-" + UUID.randomUUID() + "@example.test";
        String code = requestCode(email);
        MvcResult result = mvc.perform(post("/api/v1/auth/verify-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new dev.apisentinel.auth.AuthController.VerifyRequest(
                                email, code, "SIGN_UP"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String requestCode(String email) throws Exception {
        clearInvocations(mailSender);
        mvc.perform(post("/api/v1/auth/request-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new dev.apisentinel.auth.AuthController.CodeRequest(email, "SIGN_UP"))))
                .andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        Matcher matcher = Pattern.compile("code is ([0-9]{6})\\.").matcher(message.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
        return matcher.group(1);
    }
}
