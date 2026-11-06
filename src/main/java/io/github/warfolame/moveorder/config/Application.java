package io.github.warfolame.moveorder.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the move order tracker. Spring scans {@code config}, {@code persistence} and
 * {@code handler}; {@code domain} has no annotations and its objects are created in
 * {@link MoveOrderRulesConfig}.
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.warfolame.moveorder.config",
        "io.github.warfolame.moveorder.persistence",
        "io.github.warfolame.moveorder.handler"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
