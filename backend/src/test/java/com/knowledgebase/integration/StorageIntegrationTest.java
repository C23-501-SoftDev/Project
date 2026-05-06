package com.knowledgebase.integration;

import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorageIntegrationTest extends IntegrationTestBase {

    @Test
    void storageIsInitialized_andHealthEndpointIsUp() throws Exception {
        // Проверяем, что StorageConfig создал директории и инициализировал git repo.
        assertTrue(Files.exists(tempDir.resolve("git-repo")));
        assertTrue(Files.exists(tempDir.resolve("git-repo").resolve(".git")));
        assertTrue(Files.exists(tempDir.resolve("blob-storage")));
        assertTrue(Files.exists(tempDir.resolve("blob-storage").resolve("images")));
        assertTrue(Files.exists(tempDir.resolve("blob-storage").resolve("attachments")));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}

