package com.tickethub.event.service.impl;

import com.tickethub.event.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class S3StorageService implements StorageService {

    @Value("${aws.s3.bucket:tickethub-media-bucket}")
    private String bucket;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpoint; // e.g. http://localstack:4566 or blank for AWS S3

    @Value("${aws.s3.enabled:false}")
    private boolean s3Enabled;

    private final Path localUploadDir = Paths.get("uploads", "media");

    public S3StorageService() {
        try {
            Files.createDirectories(localUploadDir);
        } catch (IOException e) {
            log.warn("Could not create local upload directory: {}", e.getMessage());
        }
    }

    @Override
    public String upload(String directory, String filename, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new com.tickethub.event.exception.BusinessException("Uploaded file cannot be empty.");
        }
        String safeDir = directory != null ? directory : "";
        if (safeDir.contains("..") || (filename != null && filename.contains(".."))) {
            throw new com.tickethub.event.exception.BusinessException("Invalid path: path traversal is not allowed.");
        }

        String safeFilename = (filename == null || filename.isBlank()) ? "upload.bin" : filename;
        String cleanDir = safeDir.replaceAll("^/+", "").replaceAll("/+$", "");
        String sanitizedName = UUID.randomUUID() + "-" + safeFilename.replaceAll("[^a-zA-Z0-9.-]", "_").replace("..", "_");
        String key = cleanDir.isEmpty() ? sanitizedName : cleanDir + "/" + sanitizedName;

        Path targetFile = resolveSafePath(key);
        try {
            Files.createDirectories(targetFile.getParent());
            try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                fos.write(content);
            }
            log.info("Stored media locally at: {}", targetFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to store media file locally", e);
            throw new RuntimeException("Could not store media file", e);
        }

        if (s3Enabled) {
            log.info("Simulating/Executing S3 Upload to bucket: {}, region: {}, key: {}, contentType: {}",
                    bucket, region, key, contentType);
            if (endpoint != null && !endpoint.isBlank()) {
                return endpoint + "/" + bucket + "/" + key;
            }
            return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        }

        return "/api/media/" + key;
    }

    @Override
    public byte[] download(String key) {
        Path targetFile = resolveSafePath(key);
        if (Files.exists(targetFile)) {
            try {
                return Files.readAllBytes(targetFile);
            } catch (IOException e) {
                log.error("Could not read local media file: {}", key, e);
            }
        }
        return new byte[0];
    }

    @Override
    public void delete(String key) {
        try {
            Path targetFile = resolveSafePath(key);
            Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", key, e.getMessage());
        }
    }

    private Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("..")) {
            throw new com.tickethub.event.exception.BusinessException("Invalid path: path traversal is not allowed.");
        }
        Path baseDir = localUploadDir.toAbsolutePath().normalize();
        Path targetFile = localUploadDir.resolve(relativePath.replaceAll("^/+", "")).toAbsolutePath().normalize();
        if (!targetFile.normalize().startsWith(baseDir)) {
            throw new com.tickethub.event.exception.BusinessException("Invalid path: outside of upload directory.");
        }
        return targetFile;
    }
}
