package dev.apisentinel.analytics;

import dev.apisentinel.monitors.MonitorDtos.PerformancePoint;
import dev.apisentinel.monitors.MonitorService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final MonitorService monitors;

    public AnalyticsController(MonitorService monitors) { this.monitors = monitors; }

    @GetMapping("/performance")
    public List<PerformancePoint> performance(@AuthenticationPrincipal UUID ownerId) { return monitors.performance(ownerId); }
}