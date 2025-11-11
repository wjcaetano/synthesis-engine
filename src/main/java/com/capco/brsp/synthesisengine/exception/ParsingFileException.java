package com.capco.brsp.synthesisengine.exception;

import com.fasterxml.jackson.databind.JavaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParsingFileException extends RuntimeException {

    public ParsingFileException(Object source, JavaType javaType, Exception exception) {
        super(
                String.format("Failed to parse %s to %s!", source.getClass(), javaType.getTypeName()),
                exception
        );

    }
}
