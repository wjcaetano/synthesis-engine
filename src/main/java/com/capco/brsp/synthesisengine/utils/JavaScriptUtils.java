package com.capco.brsp.synthesisengine.utils;

import groovy.util.logging.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@lombok.extern.slf4j.Slf4j
@Slf4j
public class JavaScriptUtils {

    public static String escapeJsString(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String generateCompleteJavaScript(List<String> lines) {
        return String.join("\n", lines);
    }

    public static String minifyJs(String jsCode) {
        return jsCode.replaceAll("//.*|/\\*(.|\\R)*?\\*/", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*([{}();,:=+\\-*/<>])\\s*", "$1")
                .trim();
    }

    public static String generateFunction(String name, String[] args, String body) {
        return "function " + name + "(" + String.join(", ", args) + ") {\n" + body + "\n}";
    }

    public static String wrapInScriptTag(String jsCode) {
        return "<script>\n" + jsCode + "\n</script>";
    }

    public static boolean isValidJavaScriptIdentifier(String str) {
        return str.matches("^[a-zA-Z_$][a-zA-Z\\d_$]*$");
    }

    public static String applyTemplate(String template, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    public static String generateFunctionCall(String functionName, Object... args) {
        StringJoiner sj = new StringJoiner(", ");
        for (Object arg : args) {
            sj.add(JsonUtils.writeAsJsonString(arg, false));
        }
        return functionName + "(" + sj + ");";
    }

    public static String generateJsObject(Object objectToJs) {
        return JsonUtils.writeAsJsonString(objectToJs, false);
    }

    public static String generateVariable(String name, Object defaultValue, boolean isConstant) {
        if (isConstant) {
            return "const " + name + " = " + JsonUtils.writeAsJsonString(defaultValue, false) + ";";
        } else {
            return "let " + name + " = " + JsonUtils.writeAsJsonString(defaultValue, false) + ";";
        }
    }

    public static List<String> extractFunctionAndMethodNames(String code) {
        List<String> result = new ArrayList<>();

        // Match function declarations: function foo() {}
        Pattern functionPattern = Pattern.compile("function\\s+([a-zA-Z_$][\\w$]*)\\s*\\(");
        Matcher functionMatcher = functionPattern.matcher(code);
        while (functionMatcher.find()) {
            result.add(functionMatcher.group(1));
        }

        // Match arrow functions: const foo = () => {}
        Pattern arrowFunctionPattern = Pattern.compile("(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z_$][\\w$]*)\\s*=>");
        Matcher arrowMatcher = arrowFunctionPattern.matcher(code);
        while (arrowMatcher.find()) {
            result.add(arrowMatcher.group(1));
        }

        // Match class methods: methodName() { ... }
        Pattern methodPattern = Pattern.compile("(?<=\\n|^)\\s*([a-zA-Z_$][\\w$]*)\\s*\\([^)]*\\)\\s*\\{");
        Matcher methodMatcher = methodPattern.matcher(code);
        while (methodMatcher.find()) {
            String name = methodMatcher.group(1);
            if (!result.contains(name)) {
                result.add(name);
            }
        }

        return result;
    }

    public static List<Map<String, Object>> extractFunctionsWithBodies(String code) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Match function declarations: function foo() {}
        Pattern funcPattern = Pattern.compile("function\\s+([a-zA-Z_$][\\w$]*)\\s*\\([^)]*\\)\\s*\\{");
        Matcher funcMatcher = funcPattern.matcher(code);
        while (funcMatcher.find()) {
            String name = funcMatcher.group(1);
            int bodyStart = funcMatcher.end() - 1;
            String body = extractBlock(code, bodyStart);
            Map<String, Object> functionsAndMethods = new ConcurrentLinkedHashMap<>();
            functionsAndMethods.put(name, body);
            result.add(functionsAndMethods);
        }

        // Match arrow functions: const foo = () => {}
        Pattern arrowPattern = Pattern.compile("(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z_$][\\w$]*)\\s*=>\\s*\\{");
        Matcher arrowMatcher = arrowPattern.matcher(code);
        while (arrowMatcher.find()) {
            String name = arrowMatcher.group(1);
            int bodyStart = arrowMatcher.end() - 1;
            String body = extractBlock(code, bodyStart);
            Map<String, Object> functionsAndMethods = new ConcurrentLinkedHashMap<>();
            functionsAndMethods.put(name, body);
            result.add(functionsAndMethods);
        }

        // Match class methods: methodName() { ... }
        Pattern methodPattern = Pattern.compile("(?<=\\n|^)\\s*([a-zA-Z_$][\\w$]*)\\s*\\([^)]*\\)\\s*\\{");
        Matcher methodMatcher = methodPattern.matcher(code);
        while (methodMatcher.find()) {
            String name = methodMatcher.group(1);
            int bodyStart = methodMatcher.end() - 1;
            String body = extractBlock(code, bodyStart);
            Map<String, Object> functionsAndMethods = new ConcurrentLinkedHashMap<>();
            functionsAndMethods.put(name, body);
            result.add(functionsAndMethods);
        }

        return result;
    }

    private static String extractBlock(String code, int startIndex) {
        int openBraces = 0;
        int index = startIndex;
        for (; index < code.length(); index++) {
            char c = code.charAt(index);
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;

            if (openBraces == 0) break;
        }
        return code.substring(startIndex, index + 1);
    }
}