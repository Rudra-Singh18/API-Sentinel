package dev.apisentinel.notifications;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);
    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String smtpHost;
    private final String from;
    private final String publicUrl;

    public MailNotificationService(ObjectProvider<JavaMailSender> senderProvider,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${sentinel.mail-from:noreply@apisentinel.local}") String from,
            @Value("${sentinel.public-url:http://localhost:5173}") String publicUrl) {
        this.senderProvider = senderProvider; this.smtpHost = smtpHost; this.from = from; this.publicUrl = publicUrl;
    }

    public boolean isConfigured() {
        return !smtpHost.isBlank() && senderProvider.getIfAvailable() != null;
    }

    public boolean sendTest(String recipient) {
        return send(recipient, "API Sentinel test email", "Email delivery is configured for this API Sentinel account.");
    }

    public boolean sendAuthenticationCode(String recipient, String code) {
        return send(recipient, "Your API Sentinel sign-in code",
                "Your verification code is " + code + ". It expires in 10 minutes and can only be used once.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void incidentOpened(IncidentOpenedEvent event) {
        String body = "Monitor: " + event.monitorName() + "\nEndpoint host: " + event.host()
                + "\nDetected: " + DateTimeFormatter.ISO_INSTANT.format(event.startedAt())
                + "\nObserved failure: " + event.summary()
                + "\nIncident: " + publicUrl + "/app/incidents/" + event.incidentId();
        send(event.recipient(), "API Sentinel incident: " + event.monitorName(), body);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void incidentRecovered(IncidentRecoveredEvent event) {
        long minutes = Math.max(0, Duration.between(event.startedAt(), event.recoveredAt()).toMinutes());
        String body = "Monitor: " + event.monitorName() + "\nRecovered: " + DateTimeFormatter.ISO_INSTANT.format(event.recoveredAt())
                + "\nIncident duration: " + minutes + " minutes"
                + "\nIncident: " + publicUrl + "/app/incidents/" + event.incidentId();
        send(event.recipient(), "API Sentinel recovered: " + event.monitorName(), body);
    }

    private boolean send(String recipient, String subject, String body) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (smtpHost.isBlank() || sender == null) {
            log.info("notification_not_sent reason=smtp_not_configured");
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (RuntimeException exception) {
            log.warn("notification_delivery_failed type={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}