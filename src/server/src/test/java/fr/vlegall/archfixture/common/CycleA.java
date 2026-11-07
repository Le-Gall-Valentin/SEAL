package fr.vlegall.archfixture.common;

import fr.vlegall.archfixture.security.CycleB;

/** Interdit, avec CycleB : un cycle entre packages de premier niveau. */
public class CycleA {
    CycleB other;
}
