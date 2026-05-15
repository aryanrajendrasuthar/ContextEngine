
package com.contextengine.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Buckets rate limit tokens by organization ID.
     * Each organization gets its own independent token bucket, so one
     * high-volume tenant cannot degrade throughput for others.
     *
     * Falls back to "anonymous" for unauthenticated requests (auth routes)
     * so they share a single bucket rather than bypassing limits entirely.
     */
    @Bean
    @Primary
    public KeyResolver organizationKeyResolver() {
        return exchange -> {
            String orgId = exchange.getRequest().getHeaders().getFirst("X-Organization-Id");
            return Mono.just(orgId != null && !orgId.isBlank() ? orgId : "anonymous");
        };
    }
}
