package fr.vlegall.archfixture.alpha.domain.model;

import fr.vlegall.archfixture.security.FixtureJournal;

/** Interdit : un domaine qui dépend du package technique security. */
public class DomainUsingSecurity {
    void log(FixtureJournal journal) {
        journal.write();
    }
}
