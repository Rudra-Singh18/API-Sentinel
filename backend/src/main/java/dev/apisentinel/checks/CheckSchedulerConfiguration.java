package dev.apisentinel.checks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
public class CheckSchedulerConfiguration {
    @Bean
    HttpClient monitorHttpClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean(name = "monitorExecutor")
    ThreadPoolTaskExecutor monitorExecutor(@Value("${sentinel.worker-concurrency:8}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("monitor-check-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}