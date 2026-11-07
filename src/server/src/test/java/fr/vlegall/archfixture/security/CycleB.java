package fr.vlegall.archfixture.security;

import fr.vlegall.archfixture.common.CycleA;

/** Interdit, avec CycleA : un cycle entre packages de premier niveau. */
public class CycleB {
    CycleA other;
}
