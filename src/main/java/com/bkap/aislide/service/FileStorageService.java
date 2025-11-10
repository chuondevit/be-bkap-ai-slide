// src/main/java/com/bkap/aislide/service/FileStorageService.java
package com.bkap.aislide.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @jakarta.annotation.PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(uploadDir));
    }

    public String save(String taskId, byte[] data, String ext) throws IOException {
        String filename = taskId + "." + ext;
        Path path = Paths.get(uploadDir, filename);
        Files.write(path, data);
        return "/api/slides/download/" + filename;
    }
}