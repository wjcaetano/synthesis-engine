package com.capco.brsp.synthesisengine.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IllegalFilesMapException extends RuntimeException {
    public IllegalFilesMapException(Exception exception) {
        super(exception);
    }
}
