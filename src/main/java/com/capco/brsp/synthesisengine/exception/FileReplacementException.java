package com.capco.brsp.synthesisengine.exception;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class FileReplacementException extends RuntimeException {

    public FileReplacementException(Path filePath, Exception exception) {
        super(
                String.format("Error trying to do a placeholder replacement inside the file '%s'.%nError Message: %s", filePath, exception.getMessage()),
                exception
        );
    }
}
