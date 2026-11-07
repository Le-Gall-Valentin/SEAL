package fr.vlegall.archfixture.alpha.infrastructure.journal;

import fr.vlegall.archfixture.security.FixtureJournal;

/** Permis : l'infrastructure d'un contexte utilise le package technique security. */
public class JournalAdapter {
    void log(FixtureJournal journal) {
        journal.write();
    }
}
