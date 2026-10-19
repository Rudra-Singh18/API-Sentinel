package dev.apisentinel.checks;

import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CheckScheduler {
    private final CheckScheduleService schedule;
    private final MonitorCheckService checker;
    private final ThreadPoolTaskExecutor executor;

    public CheckScheduler(CheckScheduleService schedule, MonitorCheckService checker, ThreadPoolTaskExecutor monitorExecutor) {
        this.schedule = schedule; this.checker = checker; this.executor = monitorExecutor;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void scheduleDueChecks() {
        List<UUID> dueIds = schedule.claimDue(100);
        for (UUID id : dueIds) {
            try {
                executor.execute(() -> checker.check(id));
            } catch (RuntimeException exception) {
                schedule.defer(id);
            }
        }
    }
}