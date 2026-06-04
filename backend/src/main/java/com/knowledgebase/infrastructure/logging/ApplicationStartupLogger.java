package com.knowledgebase.infrastructure.logging;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

@Component
public class ApplicationStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final SystemLogger log = SystemLogger.getLogger(ApplicationStartupLogger.class, "application");
    private final Environment environment;
    private final DataSource dataSource;

    public ApplicationStartupLogger(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info(
                "Application started successfully",
                "application_start",
                "success",
                "application", environment.getProperty("spring.application.name", "knowledge-base-backend"),
                "port", environment.getProperty("server.port", "8080"),
                "profiles", activeProfiles()
        );

        verifyDatabaseConnection();
    }

    private void verifyDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            log.info(
                    "Database connection verified",
                    "database_connect",
                    "success",
                    "database_product", connection.getMetaData().getDatabaseProductName()
            );
        } catch (SQLException ex) {
            log.error("Database connection failed", "database_connect", ex);
        }
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(",", Arrays.asList(profiles));
    }
}
