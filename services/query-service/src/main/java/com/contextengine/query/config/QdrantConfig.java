
package com.contextengine.query.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class QdrantConfig {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    @Bean("qdrantWebClient")
    public WebClient qdrantWebClient(WebClient.Builder builder) {
        WebClient.Builder b = builder.baseUrl("http://" + host + ":" + port);
        if (apiKey != null && !apiKey.isBlank()) {
            b = b.defaultHeader("api-key", apiKey);
        }
        return b.build();
    }
}
