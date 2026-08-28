package com.neaz.travel.visa.config;

import com.neaz.travel.visa.application.monitoring.SourceChangeMonitor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Triggers {@link SourceChangeMonitor} on a fixed interval. Contains no
 * monitoring logic itself — that lives entirely in the application layer.
 */
@Configuration
@EnableScheduling
public class SourceMonitoringConfig {

    private final SourceChangeMonitor sourceChangeMonitor;

    public SourceMonitoringConfig(SourceChangeMonitor sourceChangeMonitor) {
        this.sourceChangeMonitor = sourceChangeMonitor;
    }

    @Scheduled(fixedDelayString = "${source-monitoring.check-interval-ms}")
    public void runScheduledCheck() {
        sourceChangeMonitor.checkAll();
    }
}
