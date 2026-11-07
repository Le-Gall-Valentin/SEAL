package fr.vlegall.archfixture.alpha.infrastructure.web;

import fr.vlegall.seal.common.ApplicationService;

/** Interdit : un service applicatif hors de application/handler. */
@ApplicationService
public class MisplacedApplicationService {}
