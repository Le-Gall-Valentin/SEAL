package fr.vlegall.seal.ws.infrastructure.session;

import fr.vlegall.seal.common.SealProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** TRN-12, ADR 0043 (point 5), ADR 0044 : planifie la ronde des WebSockets à l'intervalle {@code seal.ws.sweep-delay}. */
@Configuration(proxyBeanMethods = false)
public class SessionSweepScheduling implements SchedulingConfigurer {

    private final SessionWatch watch;
    private final Duration delay;

    SessionSweepScheduling(SessionWatch watch, Duration delay) {
        this.watch = watch;
        this.delay = delay;
    }

    @Autowired
    public SessionSweepScheduling(SessionWatch watch, SealProperties properties) {
        this(watch, properties.ws().sweepDelay());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(watch::sweep, delay);
    }
}
