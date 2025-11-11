package com.capco.brsp.synthesisengine.exception;

import org.springframework.expression.spel.SpelEvaluationException;

public class SpelEvaluationExceptionDetails extends RuntimeException {

    private final String expression;
    private final int position;
    private final String message;
    private final SpelEvaluationException originalException;

    public SpelEvaluationExceptionDetails(String originalExpression, SpelEvaluationException ex) {
        this.expression = originalExpression;
        this.position = ex.getPosition();
        this.message = ex.getMessage();
        this.originalException = ex;
    }

    public SpelEvaluationExceptionDetails(SpelEvaluationException ex) {
        this.expression = ex.getExpressionString();
        this.position = ex.getPosition();
        this.message = ex.getMessage();
        this.originalException = ex;
    }

    public String getExpression() {
        return expression;
    }

    public int getPosition() {
        return position;
    }

    public String getMessage() {
        return message;
    }

    public SpelEvaluationException getOriginalException() {
        return originalException;
    }

    public String getSnippetAroundError(int contextSize) {
        if (expression == null || expression.isEmpty()) return "";

        String innerExpr = expression;
        int prefixOffset = 0;

        if (expression.startsWith("${") && expression.endsWith("}")) {
            innerExpr = expression.substring(2, expression.length() - 1);
            prefixOffset = 2;
        }

        int start = Math.max(0, position - contextSize);
        int end = Math.min(innerExpr.length(), position + contextSize);

        String snippet = innerExpr.substring(start, end);
        int markerPosition = position - start;

        return "${" + snippet + "}" + '\n' +
                " ".repeat(Math.max(0, markerPosition + prefixOffset)) + "^\n";
    }

    @Override
    public String toString() {
        return "SpEL error at position " + position +
                " in expression:\n" +
                getSnippetAroundError(20) +
                "\nFull Expression: " + expression +
                "\nMessage: " + message + "\n" +
                "Cause: " + (originalException.getCause() != null ? originalException.getCause().getMessage() : "None");
    }
}
