package dev.apisentinel.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private JavaMailSender mailSender;

    @Test
    void csrfEndpointIssuesReadableRequestTokenCookie() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
        void verifiedSignUpPersistsAccountAndAuthenticatesSubsequentRequests() throws Exception {
        String code = requestCode("avery@example.test", "SIGN_UP");
        String payload = json.writeValueAsString(new AuthController.VerifyRequest("avery@example.test", code, "SIGN_UP"));
        MvcResult result = mvc.perform(post("/api/v1/auth/verify-code").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("avery@example.test")).andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/monitors").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/monitors")).andExpect(status().isUnauthorized());
    }

    @Test
        void signInVerifiesEmailAndCodeCannotBeReused() throws Exception {
        String email = "returning@example.test";
        String signupCode = requestCode(email, "SIGN_UP");
        mvc.perform(post("/api/v1/auth/verify-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.VerifyRequest(email, signupCode, "SIGN_UP"))))
            .andExpect(status().isOk());

        String signInCode = requestCode(email, "SIGN_IN");
        String wrongCode = signInCode.equals("000000") ? "000001" : "000000";
        mvc.perform(post("/api/v1/auth/verify-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.VerifyRequest(email, wrongCode, "SIGN_IN"))))
            .andExpect(status().isUnauthorized());
        MvcResult result = mvc.perform(post("/api/v1/auth/verify-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.VerifyRequest(email, signInCode, "SIGN_IN"))))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/auth/me").session((MockHttpSession) result.getRequest().getSession(false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        mvc.perform(post("/api/v1/auth/verify-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.VerifyRequest(email, signInCode, "SIGN_IN"))))
            .andExpect(status().isUnauthorized());
        }

        @Test
        void signInDoesNotCreateAnUnregisteredAccount() throws Exception {
        String email = "unknown@example.test";
        String code = requestCode(email, "SIGN_IN");
        mvc.perform(post("/api/v1/auth/verify-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.VerifyRequest(email, code, "SIGN_IN"))))
            .andExpect(status().isUnauthorized());
        }

        private String requestCode(String email, String intent) throws Exception {
            clearInvocations(mailSender);
        mvc.perform(post("/api/v1/auth/request-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.CodeRequest(email, intent))))
            .andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        Matcher matcher = Pattern.compile("code is ([0-9]{6})\\.").matcher(message.getValue().getText());
        Assertions.assertTrue(matcher.find());
        return matcher.group(1);
    }
}