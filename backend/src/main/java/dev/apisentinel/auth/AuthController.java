package dev.apisentinel.auth;

import dev.apisentinel.notifications.MailNotificationService;
import dev.apisentinel.security.SessionAuthenticationFilter;
import dev.apisentinel.users.AppUser;
import dev.apisentinel.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final CookieCsrfTokenRepository csrfTokens;
    private final AuthCodeService authCodes;
    private final MailNotificationService mail;
    private final SecureRandom random = new SecureRandom();

    public AuthController(UserRepository users, CookieCsrfTokenRepository csrfTokens,
            AuthCodeService authCodes, MailNotificationService mail) {
        this.users = users; this.csrfTokens = csrfTokens;
        this.authCodes = authCodes; this.mail = mail;
    }

    public record CodeRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank String intent) {}
    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 6, max = 6) String code, @NotBlank String intent) {}
    public record UserResponse(UUID id, String name, String email, Instant createdAt) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        csrfTokens.saveToken(token, request, response);
        return Map.of("token", token.getToken());
    }

    @PostMapping("/request-code")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public synchronized void requestCode(@Valid @RequestBody CodeRequest input) {
        String intent = normalizeIntent(input.intent());
        String email = normalize(input.email());
        if (authCodes.isCoolingDown(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait a minute before requesting another code.");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        if (!mail.sendAuthenticationCode(email, code)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured. Contact the workspace administrator.");
        }
        authCodes.store(email, intent, code);
    }

    @PostMapping("/verify-code")
    @Transactional
    public UserResponse verifyCode(@Valid @RequestBody VerifyRequest input, HttpServletRequest request) {
        String email = normalize(input.email());
        String intent = normalizeIntent(input.intent());
        if (!authCodes.verify(email, intent, input.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That code is invalid or expired. Request a new code and try again.");
        }
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null && intent.equals("SIGN_IN")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No account was found for that email. Create an account first.");
        }
        if (user != null && intent.equals("SIGN_UP")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email. Sign in instead.");
        }
        if (user == null) {
            String name = email.substring(0, email.indexOf('@'));
            if (name.length() > 100) name = name.substring(0, 100);
            user = users.save(new AppUser(name, email));
        }
        attachSession(request, user.getId());
        return response(user);
    }

    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserResponse me(@AuthenticationPrincipal UUID userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        AppUser user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return response(user);
    }

    private void attachSession(HttpServletRequest request, UUID userId) {
        var oldSession = request.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        request.getSession(true).setAttribute(SessionAuthenticationFilter.USER_ID_ATTRIBUTE, userId);
    }

    private UserResponse response(AppUser user) { return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt()); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String normalizeIntent(String intent) {
        String normalized = intent.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("SIGN_IN") && !normalized.equals("SIGN_UP")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose sign in or sign up.");
        }
        return normalized;
    }
}