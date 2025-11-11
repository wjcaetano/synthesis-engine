package com.capco.brsp.synthesisengine.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;

@Slf4j
@RestController
public class FilesController {

    private static final String BASE_DIR = "data\\uploads\\monolith";

    @GetMapping("/files/monolith/{folder}/{filename}")
    public ResponseEntity<FileSystemResource> serveMonolithFile(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename) {
        // Basic sanitization to avoid path traversal
        if (folder.contains("..") || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Path filePath = Path.of(BASE_DIR, folder, filename);
        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = resolveContentType(filename);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        return new ResponseEntity<>(new FileSystemResource(file), headers, HttpStatus.OK);
    }

    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if (lower.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    }
}
