package com.knowledgebase.infrastructure.health;

import com.knowledgebase.application.service.StorageService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health-check для Git и Blob хранилищ.
 * Доступен через /actuator/health
 */
@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageService storageService;

    public StorageHealthIndicator(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public Health health() {
        if (storageService.isStorageAccessible()) {
            return Health.up()
                    .withDetail("git-repo", storageService.getGitRepoPath().toString())
                    .withDetail("blob-storage", storageService.getBlobStoragePath().toString())
                    .build();
        } else {
            return Health.down()
                    .withDetail("error", "Хранилища недоступны")
                    .withDetail("git-repo", storageService.getGitRepoPath().toString())
                    .withDetail("blob-storage", storageService.getBlobStoragePath().toString())
                    .build();
        }
    }
}