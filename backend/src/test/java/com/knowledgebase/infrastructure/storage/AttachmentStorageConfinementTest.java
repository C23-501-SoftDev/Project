package com.knowledgebase.infrastructure.storage;

import com.knowledgebase.application.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentStorageConfinementTest {
    @TempDir Path temporary;

    @Test
    void readsOrdinaryAttachmentsButRejectsTraversalAndAbsoluteOutsidePaths() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("blobs"));
        Path owned = Files.createDirectories(root.resolve("attachments/document-1"));
        Files.writeString(owned.resolve("owned.txt"), "owned bytes");
        Path outside = Files.writeString(temporary.resolve("outside.txt"), "private bytes");
        var repository = repository(root);
        try (var input = repository.open("attachments/document-1/owned.txt")) {
            assertEquals("owned bytes", new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThrows(IOException.class, () -> repository.open("../outside.txt"));
        assertThrows(IOException.class, () -> repository.open(outside.toString()));
    }

    @Test
    void rejectsFilesAndDirectoriesSymlinkedOutsideStorageRoot() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("blobs"));
        Path owned = Files.createDirectories(root.resolve("attachments/document-1"));
        Path outside = Files.createDirectory(temporary.resolve("private"));
        Path secret = Files.writeString(outside.resolve("secret.txt"), "private bytes");
        Files.createSymbolicLink(owned.resolve("file-link"), secret);
        Files.createSymbolicLink(owned.resolve("directory-link"), outside);
        var repository = repository(root);
        assertThrows(IOException.class, () -> repository.open("attachments/document-1/file-link"));
        assertThrows(IOException.class, () -> repository.open("attachments/document-1/directory-link/secret.txt"));
    }

    private FileSystemAttachmentFileStorageRepository repository(Path root) {
        StorageService storage = mock(StorageService.class);
        when(storage.getBlobStoragePath()).thenReturn(root);
        return new FileSystemAttachmentFileStorageRepository(storage);
    }
}
