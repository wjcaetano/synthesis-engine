package com.capco.brsp.synthesisengine.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
@Slf4j
public final class PythonException extends Exception {
    private final String pythonType;
    private final String pythonTrace;
    private final Object pythonReturn;
    private final Map<String, Object> pythonVars;

    public PythonException(String pythonType, String message, String pythonTrace,
                                    Object pythonReturn, Map<String, Object> pythonVars) {
        super("PythonError [" + pythonType + "]: " + message
                + (pythonTrace != null && !pythonTrace.isEmpty() ? "\n" + pythonTrace : ""));
        this.pythonType = pythonType;
        this.pythonTrace = pythonTrace;
        this.pythonReturn = pythonReturn;
        this.pythonVars = pythonVars == null ? Map.of() : pythonVars;
    }

    public String getPythonType() { return pythonType; }
    public String getPythonTrace() { return pythonTrace; }
    public Object getPythonReturn() { return pythonReturn; }
    public Map<String, Object> getPythonVars() { return pythonVars; }
}
