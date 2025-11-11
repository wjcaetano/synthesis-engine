package com.capco.brsp.synthesisengine.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
@Slf4j
public class ParsingException extends RuntimeException {
    private static final String COLOR_START_RED = "\u001B[31m";
    private static final String COLOR_STOP = "\u001B[0m";

    private final String sourceName;
    private final int line;
    private final int charPositionInLine;
    private final String parsingInput;

    public ParsingException(String sourceName, int line, int charPositionInLine, String message, String parsingInput) {
        super(message);
        this.sourceName = sourceName;
        this.line = line;
        this.charPositionInLine = charPositionInLine;
        this.parsingInput = parsingInput;

        log.error("""
                {}Parsing Error
                    Details:
                    - Source: {}
                    - Line: {}
                    - Column: {}
                    - Error: {}
                    - Input:
                    {}{}""", COLOR_START_RED, sourceName, line, charPositionInLine, message, parsingInput, COLOR_STOP);
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(Map.of(
                "sourceName", sourceName,
                "line", line,
                "charPositionInLine", charPositionInLine,
                "message", this.getMessage(),
                "parsingInput", parsingInput
        ));
    }
}
