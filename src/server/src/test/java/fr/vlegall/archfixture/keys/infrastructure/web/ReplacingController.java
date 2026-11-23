package fr.vlegall.archfixture.keys.infrastructure.web;

import fr.vlegall.archfixture.pki.application.port.in.ReplaceCertificateUseCase;

/** Interdit : un contrôleur remplace un certificat sans passer par le cas d'usage qui vérifie sudo et preuve. */
public class ReplacingController {
    String reset(ReplaceCertificateUseCase replacement, String subject) {
        return replacement.supersede(subject);
    }
}
