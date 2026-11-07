package fr.vlegall.archfixture.common;

import java.time.Clock;

/** Permis : le seul endroit qui lit l'horloge système. */
public class TimeConfiguration {
    Clock clock() {
        return Clock.systemUTC();
    }
}
