package fr.vlegall.seal.ws.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class SessionSweepSchedulingTest {

    @Test
    @DisplayName("TRN-12, ADR 0043 (point 5), ADR 0044 : la ronde est planifiée à l'intervalle réglé")
    void trn12SweepIsScheduledAtTheConfiguredDelay() {
        SessionWatch watch = mock(SessionWatch.class);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new SessionSweepScheduling(watch, Duration.ofSeconds(10)).configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(10));
            task.getRunnable().run();
        });
        verify(watch).sweep();
    }
}
