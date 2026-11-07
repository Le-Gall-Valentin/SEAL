package fr.vlegall.seal.security.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées, dont la purge des seaux de {@link RateLimiter}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RateLimitConfiguration {}
