package dev.apisentinel.users;

import dev.apisentinel.notifications.MailNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final UserRepository users;
    private final MailNotificationService mail;

    public AccountController(UserRepository users, MailNotificationService mail) {
        this.users = users; this.mail = mail;
    }

    public record SettingsResponse(UUID id, String name, String email, String timeZone, boolean incidentEmailsEnabled,
            boolean recoveryEmailsEnabled, boolean emailConfigured, java.time.Instant createdAt) {}
    public record ProfileRequest(@NotBlank @Size(max = 100) String name, @NotBlank String timeZone) {}
    public record NotificationRequest(boolean incidentEmailsEnabled, boolean recoveryEmailsEnabled) {}
    public record EmailStatus(boolean configured) {}

    @GetMapping
    @Transactional(readOnly = true)
    public SettingsResponse get(@AuthenticationPrincipal UUID ownerId) {
        return response(requireUser(ownerId));
    }

    @PatchMapping("/profile")
    @Transactional
    public SettingsResponse updateProfile(@AuthenticationPrincipal UUID ownerId, @Valid @RequestBody ProfileRequest input) {
        AppUser user = requireUser(ownerId);
        String zone;
        try {
            zone = ZoneId.of(input.timeZone().trim()).getId();
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid time zone.");
        }
        user.setName(input.name().trim());
        user.setTimeZone(zone);
        return response(user);
    }

    @PatchMapping("/notifications")
    @Transactional
    public SettingsResponse updateNotifications(@AuthenticationPrincipal UUID ownerId, @Valid @RequestBody NotificationRequest input) {
        AppUser user = requireUser(ownerId);
        user.setIncidentEmailsEnabled(input.incidentEmailsEnabled());
        user.setRecoveryEmailsEnabled(input.recoveryEmailsEnabled());
        return response(user);
    }

    @GetMapping("/notifications/status")
    public EmailStatus notificationStatus() {
        return new EmailStatus(mail.isConfigured());
    }

    @PostMapping("/notifications/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendTestEmail(@AuthenticationPrincipal UUID ownerId) {
        AppUser user = requireUser(ownerId);
        if (!mail.sendTest(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured or the test message could not be sent.");
        }
    }

    private AppUser requireUser(UUID ownerId) {
        return users.findById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private SettingsResponse response(AppUser user) {
        return new SettingsResponse(user.getId(), user.getName(), user.getEmail(), user.getTimeZone(),
                user.isIncidentEmailsEnabled(), user.isRecoveryEmailsEnabled(), mail.isConfigured(), user.getCreatedAt());
    }
}
