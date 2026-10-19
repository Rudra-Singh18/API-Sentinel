package dev.apisentinel.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.security.UrlSafetyPolicy;
import dev.apisentinel.users.AppUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class MonitorCheckServiceTest {
    private final MonitorRepository monitors = mock(MonitorRepository.class);
    private final MonitorStateService state = mock(MonitorStateService.class);
    private final UrlSafetyPolicy policy = mock(UrlSafetyPolicy.class);
    private final HttpClient client = mock(HttpClient.class);
    private final MonitorCheckService service = new MonitorCheckService(monitors, state, policy, client);
    private final Monitor monitor = new Monitor(new AppUser("Avery", "avery@example.test"),
            "API", "https://public.example.test/health", "GET", 200, 60, 5000, 2, 2);

    @BeforeEach
    void permitOnlyTheMockedTarget() {
        when(policy.validate(monitor.getUrl())).thenReturn(URI.create(monitor.getUrl()));
    }

    @Test
    void recordsMatchingHttpStatusAsAvailable() throws Exception {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any())).thenReturn(response);

        CheckOutcome outcome = service.execute(monitor);

        assertTrue(outcome.available());
        assertEquals(200, outcome.httpStatus());
        assertEquals(null, outcome.failureCategory());
        verify(policy).validate(monitor.getUrl());
    }

    @Test
    void recordsUnexpectedStatusAsAnAvailabilityFailure() throws Exception {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any())).thenReturn(response);

        CheckOutcome outcome = service.execute(monitor);

        assertFalse(outcome.available());
        assertEquals("HTTP_STATUS", outcome.failureCategory());
        assertTrue(outcome.diagnosticMessage().contains("503"));
    }

    @Test
    void classifiesTimeoutAndUsesConfiguredMethod() throws Exception {
        Monitor headMonitor = new Monitor(monitor.getOwner(), "HEAD monitor", monitor.getUrl(), "HEAD", 200, 60, 5000, 2, 2);
        doThrow(new HttpTimeoutException("private socket detail")).when(client)
                .send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());

        CheckOutcome outcome = service.execute(headMonitor);

        assertEquals("TIMEOUT", outcome.failureCategory());
        assertEquals("The endpoint did not respond before the configured timeout.", outcome.diagnosticMessage());
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("HEAD", request.getValue().method());
    }
}