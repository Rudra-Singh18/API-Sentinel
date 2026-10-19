package dev.apisentinel.checks;

import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.security.UrlSafetyPolicy;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitorCheckService {
    private static final Logger log = LoggerFactory.getLogger(MonitorCheckService.class);
    private final MonitorRepository monitors;
    private final MonitorStateService state;
    private final UrlSafetyPolicy urlPolicy;
    private final HttpClient httpClient;

    public MonitorCheckService(MonitorRepository monitors, MonitorStateService state, UrlSafetyPolicy urlPolicy, HttpClient httpClient) {
        this.monitors = monitors; this.state = state; this.urlPolicy = urlPolicy; this.httpClient = httpClient;
    }

    public void check(UUID monitorId) {
        Monitor monitor = monitors.findById(monitorId).orElse(null);
        if (monitor == null || monitor.isArchived() || !monitor.isEnabled()) return;
        CheckOutcome result = execute(monitor);
        state.record(monitorId, result);
        log.info("monitor_check_completed monitorId={} outcome={} category={} elapsedMs={}", monitorId,
                result.available() ? "AVAILABLE" : "UNAVAILABLE", result.failureCategory(), result.responseTimeMs());
    }

    CheckOutcome execute(Monitor monitor) {
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        try {
            URI uri = urlPolicy.validate(monitor.getUrl());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(monitor.getTimeoutMs()))
                    .header("User-Agent", "API-Sentinel-Monitor/1.0")
                    .header("Accept", "*/*")
                    .method(monitor.getMethod(), HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, new LimitedBodyHandler());
            long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            boolean available = response.statusCode() == monitor.getExpectedStatus();
            String category = available ? (elapsed > monitor.getResponseTimeThresholdMs() ? "SLOW_RESPONSE" : null) : "HTTP_STATUS";
            String diagnostic = available ? null : "Expected HTTP " + monitor.getExpectedStatus() + " but received " + response.statusCode() + ".";
            return new CheckOutcome(started, Instant.now(), available, response.statusCode(), elapsed, category, diagnostic);
        } catch (ResponseStatusException exception) {
            return failed(started, startNanos, "URL_BLOCKED", "Target URL is not allowed by outbound request policy.");
        } catch (java.net.http.HttpTimeoutException exception) {
            return failed(started, startNanos, "TIMEOUT", "The endpoint did not respond before the configured timeout.");
        } catch (UnknownHostException exception) {
            return failed(started, startNanos, "DNS", "The endpoint hostname could not be resolved.");
        } catch (SSLException exception) {
            return failed(started, startNanos, "TLS", "A secure connection to the endpoint could not be established.");
        } catch (ConnectException exception) {
            return failed(started, startNanos, "CONNECTION", "The endpoint refused or could not accept a connection.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(started, startNanos, "INTERRUPTED", "The endpoint check was interrupted.");
        } catch (IOException | RuntimeException exception) {
            return failed(started, startNanos, "NETWORK", "The endpoint could not be checked due to a network error.");
        }
    }

    private CheckOutcome failed(Instant started, long startNanos, String category, String message) {
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        return new CheckOutcome(started, Instant.now(), false, null, elapsed, category, message);
    }
}