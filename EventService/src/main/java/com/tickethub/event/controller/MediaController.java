package com.tickethub.event.controller;

import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media")
public class MediaController {

    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload media asset")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded file cannot be empty.");
        }
        String filename = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename()
                : "upload.bin";
        String url = storageService.upload(folder, filename, file.getBytes(), file.getContentType());
        return ResponseEntity.ok(Map.of("url", url, "filename", filename));
    }

    @GetMapping("/{folder}/{filename:.+}")
    @Operation(summary = "Download media asset from folder")
    public ResponseEntity<byte[]> getMedia(
            @PathVariable String folder,
            @PathVariable String filename) {
        return serveByKey(folder + "/" + filename);
    }

    @GetMapping("/{folder}/{subfolder}/{filename:.+}")
    @Operation(summary = "Download media asset from nested folder")
    public ResponseEntity<byte[]> getNestedMedia(
            @PathVariable String folder,
            @PathVariable String subfolder,
            @PathVariable String filename) {
        return serveByKey(folder + "/" + subfolder + "/" + filename);
    }

    @GetMapping("/{filename:.+}")
    @Operation(summary = "Download root media asset")
    public ResponseEntity<byte[]> getRootMedia(@PathVariable String filename) {
        return serveByKey(filename);
    }

    private ResponseEntity<byte[]> serveByKey(String key) {
        byte[] content = storageService.download(key);
        if (content == null || content.length == 0) {
            throw new ResourceNotFoundException("Media not found: " + key);
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(key)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(content);
    }
}
