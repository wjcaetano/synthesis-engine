package com.capco.brsp.synthesisengine.listeners;

import com.capco.brsp.synthesisengine.exception.ParsingException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import java.text.MessageFormat;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class DescriptiveErrorListener extends BaseErrorListener {
    private static final String COLOR_START_RED = "\u001B[31m";
    private static final String COLOR_START_YELLOW = "\u001B[33m";
    private static final String COLOR_START_GREEN = "\u001B[32m";
    private static final String COLOR_STOP = "\u001B[0m";

    private static final DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();
    private static final boolean REPORT_SYNTAX_ERRORS = true;

    @Getter
    @Setter
    private boolean throwException = true;

    private DescriptiveErrorListener() {
    }

    public static DescriptiveErrorListener getInstance() {
        return INSTANCE;
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        super.reportAmbiguity(recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs);
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, prediction, configs);
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg, RecognitionException e) {
        if (!REPORT_SYNTAX_ERRORS) {
            return;
        }

        String sourceName = recognizer.getInputStream().getSourceName();
        String parsingInput = "<unknown>";
        if (recognizer.getInputStream() instanceof CommonTokenStream commonTokenStream
                && commonTokenStream.getTokenSource() instanceof Lexer tokenSourceLexer
                && tokenSourceLexer.getInputStream() != null) {
            parsingInput = tokenSourceLexer.getInputStream().toString();
        }

        if (this.throwException) {
            throw new ParsingException(sourceName, line, charPositionInLine, parsingInput, msg);
        } else {
            logParsingError(line, charPositionInLine, msg, parsingInput);
        }
    }

    public void logParsingError(int line, int charPositionInLine, String message, String parsingInput) {
        String lineContent = getLineContent(parsingInput, line);
        String formattedMessage = messageWithoutInput(line, charPositionInLine, message, lineContent);
        log.error(formattedMessage);
    }

    private String messageWithoutInput(int line, int charPositionInLine, String message, String lineContent) {
        return MessageFormat.format("{0}Parsing error. Line: {4}{1}{0}. Column: {4}{2}{0}. Error: {4}{3}{0}. Line Content: {4}{6}{5}", COLOR_START_RED, line, charPositionInLine, message, COLOR_START_YELLOW, COLOR_STOP, lineContent);
    }

    private String getLineContent(String input, int line) {
        var lines = input.lines().toList();
        return lines.get(line > 0 ? line - 1 : 0);
    }

    private String getLineSurroundingContent(String input, int line) {
        String lineSurroundingContent;
        try {
            var lines = input.lines().toList();
            if (line <= 1 || line >= lines.size() - 2) {
                lineSurroundingContent = line + "|" + getLineContent(input, line);
            } else {
                AtomicInteger idx = new AtomicInteger(line - 2);
                lineSurroundingContent = IntStream.rangeClosed(line - 2, line)
                        .mapToObj(lines::get)
                        .map(lineText -> (idx.getAndIncrement()) + "|" + lineText)
                        .collect(Collectors.joining("\n"));
            }
        } catch (Exception ex) {
            return input;
        }

        return lineSurroundingContent;
    }

    private String messageWithInput(String sourceName, int line, int charPositionInLine, String message, String parsingInput) {
        String lineSurroundingContent = getLineSurroundingContent(parsingInput, line);

        return MessageFormat.format("""
                ################################################################################################################
                Parsing Error
                - Source: {0}
                - Line: {1}
                - Column: {2}
                - Error: {3}
                - Input:
                
                {4}
                
                ################################################################################################################
                """, sourceName, line, charPositionInLine, message, lineSurroundingContent);
    }
}