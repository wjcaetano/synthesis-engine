package com.capco.brsp.synthesisengine.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class InMemoryMultipartFile implements MultipartFile {
    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.content = content;
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public String getOriginalFilename() {
        return this.originalFilename;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public boolean isEmpty() {
        return (this.content == null || this.content.length == 0) && (this.originalFilename == null || this.originalFilename.isEmpty());
    }

    @Override
    public long getSize() {
        return this.content.length;
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
        return this.content;
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(this.content) ;
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), this.content);
    }

    @Override
    public void transferTo(@NotNull Path path) throws IOException, IllegalStateException {
        Files.write(path, this.content);
    }
}
