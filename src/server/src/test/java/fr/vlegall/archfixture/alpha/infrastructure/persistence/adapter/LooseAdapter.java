package fr.vlegall.archfixture.alpha.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

/** Interdit : un adaptateur qui n'implémente aucun port. */
@Component
public class LooseAdapter {
    void save() {}
}
