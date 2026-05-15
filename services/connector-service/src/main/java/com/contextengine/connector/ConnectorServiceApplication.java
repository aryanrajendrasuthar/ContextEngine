
package com.contextengine.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConnectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorServiceApplication.class, args);
    }
}
