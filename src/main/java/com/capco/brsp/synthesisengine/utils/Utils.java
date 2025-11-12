package com.capco.brsp.synthesisengine.utils;

import ch.qos.logback.core.util.StringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import groovyjarjarantlr4.v4.misc.OrderedHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.awaitility.Awaitility;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.Deflater;

@Slf4j
public class Utils {
    private static final Utils INSTANCE = new Utils();

    private Utils() {
    }

    public static Utils getInstance() {
        return INSTANCE;
    }

    public static boolean isDebugMode() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .toString().contains("-agentlib:jdwp");
    }

    public static String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static String getTextBefore(String text, String regexDelimiter) {
        Pattern pattern = Pattern.compile(regexDelimiter);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            int idx = matcher.start();
            return text.substring(0, idx);
        }

        return text;
    }

    public static String getTextAfter(String text, String regexDelimiter) {
        Pattern pattern = Pattern.compile(regexDelimiter);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            int idx = matcher.start();

            if (idx + 1 < text.length()) {
                return text.substring(idx + 1);
            }
        }

        return "";
    }

    public static <T, R> R nullSafeInvoke(T obj, Function<T, R> methodRef) {
        if (obj != null) {
            return methodRef.apply(obj);
        }

        return null;
    }

    public static Map<String, Object> createWithAListOfKeys(String[] keys, Object content) {
        return createWithAListOfKeys(Arrays.stream(keys).toList(), content);
    }

    public static Map<String, Object> createWithAListOfKeys(Collection<String> keys, Object content) {
        Map<String, Object> result = new OrderedHashMap<>();

        for (var key : keys) {
            result.put(key, content);
        }

        return result;
    }

    public static Map<String, Object> flattenMap(Map<String, ?> nestedMap, String separator) {
        return nestedMap.entrySet().stream()
                .flatMap(entry -> {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (value instanceof Map<?, ?> valueMap) {
                        return flattenMap((Map<String, Object>) valueMap, separator).entrySet().stream()
                                .map(innerEntry -> Map.entry(key + separator + innerEntry.getKey(), innerEntry.getValue()));
                    } else {
                        var notNullValue = value != null ? value : "";
                        return Stream.of(Map.entry(key, notNullValue));
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static <V> Map<String, V> normalizeKeys(Map<String, V> map, String regex, String replacement) {
        return map.entrySet().stream()
                .map(it -> Map.entry(it.getKey().replaceAll(regex, replacement), it.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<String, Object> normalizeMap(Map<String, Object> map, String separator) {
        Map<String, Object> filesContent = Utils.flattenMap(map, separator);

        var escapedSeparator = StringEscapeUtils.escapeJson(separator);
        return Utils.normalizeKeys(filesContent, escapedSeparator + "{2,}", escapedSeparator);
    }

    public static Object extractMarkdownCode(Object content) {
        if (content instanceof String contentString) {
            Matcher matcher = Pattern.compile("^[\\s\\S]*```[^\\n]*\\n?([\\s\\S]+?)\\n```[\\s\\S]*$").matcher(contentString);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return content;
    }

    public static List<String> extractMarkdownCodes(Object content) {
        List<String> codes = new ConcurrentLinkedList<>();

        if (content instanceof String contentString) {
            Matcher matcher = Pattern.compile("^[\\s\\S]*```[^\\n]*\\n?([\\s\\S]+?)\\n```[\\s\\S]*$").matcher(contentString);
            while (matcher.find()) {
                codes.add(matcher.group(1));
            }
        }

        return codes;
    }

    public static boolean isMarkdownJava(String contentString) {
        return contentString != null && contentString.trim().startsWith("```java") && contentString.trim().endsWith("```");
    }

    public static String optimizeImports(String code) {
        return optimizeImports(code, "!!imports!!");
    }

    public static String optimizeImports(String code, String placeholder) {
        Pattern patternImportLine = Pattern.compile("import\\s+[a-zA-Z0-9\\-.]+;\\s*");
        List<String> lines = code.lines().toList();

        Map<Boolean, List<String>> partiotined = lines.stream().collect(Collectors.partitioningBy(patternImportLine.asPredicate()));

        String cleanCode = String.join("\n", partiotined.get(false));
        String imports = String.join("\n", partiotined.get(true));

        return cleanCode.replace(placeholder, imports);
    }

    public static Object anyCollectionGetOrSet(Object target, String path, Object defaultValue) {
        var value = anyCollectionGet(target, path);
        if (value != null) {
            return value;
        }

        anyCollectionSet(target, path, defaultValue);

        return anyCollectionGet(target, path);
    }

    public static Object anyCollectionGet(Object target, String path) {
        return anyCollectionGet(target, path, null);
    }

    public static <T> T anyCollectionGet(Object target, String path, T defaultValue) {
        try {
            return JsonPath.read(target, path);
        } catch (PathNotFoundException pnfe) {
            return defaultValue;
        }
    }

    public static Object anyCollectionSet(Object target, String path, Object value) {
        String pattern = "^(([^'\".\\[]+)|\\[(\\d*|['\"][^'\"\\[\\]]*['\"])])\\.*(.*)$";
        String key = getRegexGroup(path, pattern, 2);
        String index = getRegexGroup(path, pattern, 3);
        String rest = getRegexGroup(path, pattern, 4);

        if (key == null && index != null && (index.trim().startsWith("\"") || index.trim().startsWith("'"))) {
            String trimmedIndex = index.trim();
            key = trimmedIndex.substring(1, trimmedIndex.length() - 1);
            index = null;
        }

        if (key != null) {
            Map<String, Object> map = target instanceof Map<?, ?> ? (Map<String, Object>) target : new ConcurrentLinkedHashMap<>();
            if (StringUtil.isNullOrEmpty(rest)) {
                map.put(key, value);
            } else {
                map.put(key, anyCollectionSet(map.get(key), rest, value));
            }
            return map;
        } else if (index != null) {
            Integer intIndex = castOrDefault(index, Integer.class, null);
            List<Object> list = target instanceof List<?> ? (List<Object>) target : createEmptyList(Utils.nvl(intIndex, -1) + 1);
            if (StringUtil.isNullOrEmpty(rest)) {
                if (intIndex == null) {
                    intIndex = list.size();
                }

                if (intIndex >= list.size()) {
                    int sizeToAdd = intIndex - (list.size() - 1);
                    list.addAll(createEmptyList(sizeToAdd));
                }

                list.set(intIndex, value);
            } else {
                list.set(intIndex, anyCollectionSet(list.get(intIndex), rest, value));
            }
            return list;
        } else if (target instanceof Map<?, ?> && value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> targetMap = (Map<Object, Object>) target;
            @SuppressWarnings("unchecked")
            Map<Object, Object> valueMap = (Map<Object, Object>) value;
            targetMap.putAll(valueMap);
        }

        return null;
    }

    public static <T> T castOrDefault(Object value, Class<T> clazz, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            switch (clazz.getSimpleName()) {
                case "int", "Integer":
                    return clazz.cast(Integer.valueOf(value.toString()));
                case "double", "Double":
                    return clazz.cast(Double.valueOf(value.toString()));
                case "boolean", "Boolean":
                    return clazz.cast(Boolean.valueOf(value.toString()));
                case "char", "Character":
                    String strValue = value.toString();
                    if (strValue.length() == 1) {
                        return clazz.cast(strValue.charAt(0));
                    }
                    throw new IllegalArgumentException("Invalid Character conversion");
                case "long", "Long":
                    return clazz.cast(Long.valueOf(value.toString()));
                case "float", "Float":
                    return clazz.cast(Float.valueOf(value.toString()));
                case "BigDecimal":
                    return clazz.cast(new BigDecimal(value.toString()));
                case "short", "Short":
                    return clazz.cast(Short.valueOf(value.toString()));
                case "byte", "Byte":
                    return clazz.cast(Byte.valueOf(value.toString()));
                default:
                    return clazz.cast(value);
            }
        } catch (NumberFormatException | ClassCastException ex) {
            return defaultValue;
        }
    }

    public static String getRegexGroup(String text, String regex, int group) {
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                return matcher.group(group);
            } catch (IndexOutOfBoundsException ex) {
                return null;
            }
        }

        return null;
    }

    public static List<String> getAllRegexGroup(String text, String regex, int group) {
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(text);

        List<String> items = new ConcurrentLinkedList<>();
        while (matcher.find()) {
            try {
                items.add(matcher.group(group));
            } catch (IndexOutOfBoundsException ex) {
                return new ConcurrentLinkedList<>();
            }
        }

        return items;
    }

    public static List<List<String>> getAllRegexMatches(String text, String regex) {
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(text);

        List<List<String>> items = new ConcurrentLinkedList<>();
        while (matcher.find()) {
            try {
                List<String> groups = new ConcurrentLinkedList<>();
                groups.add(matcher.group(0));
                for (int i = 0; i < matcher.groupCount(); i++) {
                    groups.add(matcher.group(i + 1));
                }
                items.add(groups);
            } catch (IndexOutOfBoundsException ex) {
                return new ConcurrentLinkedList<>();
            }
        }

        return items;
    }

    public static String getRegexFirstNotNullGroup(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                var group = matcher.group(i);
                if (group != null) {
                    return group;
                }
            }
        }

        return null;
    }

    public static List<Object> createEmptyList(int elements) {
        List<Object> list = new ConcurrentLinkedList<>();

        while (elements-- > 0) {
            list.add(null);
        }

        return list;
    }

    public static int integerGetFromIndexOrMaxIncOrDefaultAndSetTarget(Object target, String path, int index, int defaultValue) {
        List<Integer> list = (List<Integer>) anyCollectionGet(target, path);

        String finalPath = path + "[" + index + "]";
        if (list == null) {
            anyCollectionSet(target, finalPath, defaultValue);
            return defaultValue;
        }

        int maxValue = Collections.max(list);

        return (int) anyCollectionGetOrSet(target, finalPath, maxValue + 1);
    }

    public static String simplifyPath(String path, String splitter, String delimiter) {
        if (path == null) {
            return null;
        }

        String[] parts = path.split(splitter);
        String fileName = parts[parts.length - 1];

        if (parts.length == 1) {
            return fileName;
        }

        String simplifiedPath = Arrays.stream(parts, 0, parts.length - 1)
                .map(it -> String.valueOf(it.charAt(0)))
                .collect(Collectors.joining(delimiter));

        return simplifiedPath + "." + fileName;
    }

    public static String hashString(String... args) {
        var string = String.join("|", args);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(string.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not found", e);
        }
    }

    public static <T> T nvl(T value) {
        return value;
    }

    @SafeVarargs
    public static <T> T nvl(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @SafeVarargs
    public static <T> T evl(T... values) {
        for (T value : values) {
            if (!isEmpty(value)) {
                return value;
            }
        }

        return Arrays.stream(values).findFirst().orElse(null);
    }

    public static boolean isEmpty(Object obj) {
        switch (obj) {
            case null -> {
                return true;
            }
            case String str -> {
                return str.trim().isEmpty();
            }
            case Collection<?> collection -> {
                return collection.isEmpty();
            }
            case Map<?, ?> map -> {
                return map.isEmpty();
            }
            default -> {
            }
        }

        if (obj.getClass().isArray()) {
            return Array.getLength(obj) == 0;
        }

        return false;
    }

    public static <T> T retryWhenOneOfExceptions(int attempts, long waitMillisToRetry, List<Class<?>> exceptionList, Supplier<T> supplier) throws Exception {
        int attempt = 0;
        Exception exception;
        while (attempt++ < attempts) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                exception = ex;
                if (exceptionList.stream().anyMatch(it -> ex.getClass().isAssignableFrom(it) || it.isAssignableFrom(ex.getClass()))) {
                    log.info("{} during the attempt {} of {}. Waiting {}ms before retrying!", ex.getClass().getSimpleName(), attempt, attempts, waitMillisToRetry);
                    Thread.sleep(waitMillisToRetry);
                } else {
                    throw exception;
                }

                if (attempt >= attempts) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException("Something went wrong during the retryWhenOneOfExceptions!");
    }

    public static <T> T retryWhileException(long pollMillis, long waitMillis, Predicate<Exception> exceptionTesting, Supplier<T> supplier) {
        AtomicReference<T> atomicValue = new AtomicReference<>();
        try {
            T value = supplier.get();
            atomicValue.set(value);
        } catch (Exception ex) {
            if (exceptionTesting.test(ex)) {
                Awaitility.await()
                        .pollInterval(pollMillis, TimeUnit.MILLISECONDS)
                        .atMost(waitMillis, TimeUnit.MILLISECONDS)
                        .until(() -> {
                                    try {
                                        T value = supplier.get();
                                        atomicValue.set(value);
                                    } catch (Exception ex2) {
                                        return !exceptionTesting.test(ex2);
                                    }

                                    return true;
                                }
                        );
            }
        }

        return atomicValue.get();
    }

    public static <T> T retryWhile(long pollMillis, long waitMillis, T whileValue, Supplier<T> supplier) {
        AtomicReference<T> atomicValue = new AtomicReference<>();
        Awaitility.await()
                .pollInterval(pollMillis, TimeUnit.MILLISECONDS)
                .atMost(waitMillis, TimeUnit.MILLISECONDS)
                .until(() -> {
                            T value = supplier.get();
                            atomicValue.set(value);
                            return value != whileValue;
                        }
                );

        return atomicValue.get();
    }

    public static <T> T retryWhileNot(long pollMillis, long waitMillis, T whileValue, Supplier<T> supplier) {
        AtomicReference<T> atomicValue = new AtomicReference<>();
        Awaitility.await()
                .pollInterval(pollMillis, TimeUnit.MILLISECONDS)
                .atMost(waitMillis, TimeUnit.MILLISECONDS)
                .until(() -> {
                            T value = supplier.get();
                            atomicValue.set(value);
                            return value == whileValue;
                        }
                );

        return atomicValue.get();
    }

    public static String removeColumns(String input, int columnsToRemove) {
        String[] lines = input.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            int removeCount = Math.min(columnsToRemove, line.length());

            String modifiedLine = line.substring(removeCount);

            result.append(modifiedLine).append("\n");
        }

        if (!result.isEmpty()) {
            result.deleteCharAt(result.length() - 1);
        }

        return result.toString();
    }

    public static String translate(String text, Map<String, String> map) {
        if (StringUtil.isNullOrEmpty(text) || map == null || map.isEmpty()) {
            return text;
        }

        for (var entry : map.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        return text;
    }

    public static String deflateAndEncode(String text) {
        byte[] inputBytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Deflater deflater = new Deflater();
        deflater.setInput(inputBytes);
        deflater.finish();

        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int bytesCompressed = deflater.deflate(buffer);
            byteArrayOutputStream.write(buffer, 0, bytesCompressed);
        }
        deflater.end();

        byte[] compressedData = byteArrayOutputStream.toByteArray();

        // Step 2: Remove the first two bytes and the last four bytes
        byte[] modifiedCompressedData = new byte[compressedData.length - 6]; // 2 bytes at start + 4 bytes at end
        System.arraycopy(compressedData, 2, modifiedCompressedData, 0, modifiedCompressedData.length);

        // Step 3: Base64 encode the modified compressed data
        return Base64.getEncoder().encodeToString(modifiedCompressedData);
    }

    public static String changeFileExtension(String fullPath, String newExtension) {
        Path path = Paths.get(fullPath);

        String fileNameWithoutExtension = path.getFileName().toString();
        int dotIndex = fileNameWithoutExtension.lastIndexOf('.');

        if (dotIndex != -1) {
            fileNameWithoutExtension = fileNameWithoutExtension.substring(0, dotIndex);
        }

        Path newFilePath = path.getParent().resolve(fileNameWithoutExtension + "." + newExtension);

        return newFilePath.toString();
    }

    public static String diffBetweenDates(Date start, Date end) {
        if (start == null) {
            return "00:00:00.000";
        }

        long diffInMillies = Math.abs(end.getTime() - start.getTime());

        return formatDuration(diffInMillies);
    }

    public static String formatDuration(long totalMillis) {
        Duration duration = Duration.ofMillis(totalMillis);

        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        long millis = duration.toMillis() % 1000;

        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    public static Object convertToConcurrent(Object obj) {
        return convertToConcurrent(obj, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static Object convertToConcurrent(Object obj, IdentityHashMap<Object, Object> visited) {
        if (obj == null) return null;
        if (visited.containsKey(obj)) return visited.get(obj);

        switch (obj) {
            case List<?> objects -> {
                List<Object> concurrentList = new ConcurrentLinkedList<>();
                visited.put(obj, concurrentList);
                for (Object item : objects) {
                    concurrentList.add(convertToConcurrent(item, visited));
                }
                return concurrentList;
            }
            case Set<?> objects -> {
                Set<Object> concurrentSet = new ConcurrentLinkedHashSet<>();
                visited.put(obj, concurrentSet);
                for (Object item : objects) {
                    concurrentSet.add(convertToConcurrent(item, visited));
                }
                return concurrentSet;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> concurrentMap = new ConcurrentLinkedHashMap<>();
                visited.put(obj, concurrentMap);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object value = convertToConcurrent(entry.getValue(), visited);
                    concurrentMap.put(key, value);
                }
                return concurrentMap;
            }
            default -> {
                return obj;
            }
        }
    }

    public static String[] splitFunctionArguments(String input) {
        int parenStart = input.indexOf('(');
        if (parenStart == -1 || !input.endsWith(")")) {
            return new String[]{input};  // No parentheses, treat as single token
        }

        String functionName = input.substring(0, parenStart).trim();
        String argsBody = input.substring(parenStart + 1, input.length() - 1).trim();

        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int parenDepth = 0;

        for (int i = 0; i < argsBody.length(); i++) {
            char c = argsBody.charAt(i);
            char next = i + 1 < argsBody.length() ? argsBody.charAt(i + 1) : '\0';

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') {
                    parenDepth++;
                    current.append(c);
                } else if (c == ')') {
                    parenDepth--;
                    current.append(c);
                } else if (c == ',' && parenDepth == 0) {
                    args.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            args.add(current.toString().trim());
        }

        List<String> result = new ArrayList<>();
        result.add(functionName);
        result.addAll(args);

        return result.toArray(new String[0]);
    }

    public static String encodeBase64(Object obj) {
        byte[] bytes;

        switch (obj) {
            case String text -> {
                bytes = text.getBytes(StandardCharsets.UTF_8);
            }
            case byte[] objBytes -> bytes = objBytes;
            case null -> {
                return null;
            }
            default -> {
                String text = JsonUtils.writeAsJsonString(obj, true);
                bytes = text.getBytes(StandardCharsets.UTF_8);
            }
        }

        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decodeBase64(String text) {
        String base64Content = text.trim();
        if (text.matches("^data:.*?;base64,[\\s\\S]+$")) {
            base64Content = text.substring(text.indexOf("base64,") + 7);
        }

        base64Content = base64Content.replaceAll("\\s+", "");

        return Base64.getDecoder().decode(base64Content);
    }

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([^;]+);base64,(.*)", Pattern.DOTALL);

    /**
     * Decodes a Base64-encoded string (optionally with a data URI).
     * Returns a String for text MIME types, otherwise a byte[].
     */
    public static Object decodeBase64ToAny(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String base64Content = input.trim();
        String mimeType = null;

        Matcher matcher = DATA_URI_PATTERN.matcher(base64Content);
        if (matcher.matches()) {
            mimeType = matcher.group(1).toLowerCase();
            base64Content = matcher.group(2);
        }

        // Remove all whitespace (base64 is whitespace-insensitive)
        base64Content = base64Content.replaceAll("\\s+", "");

        byte[] decodedBytes = Base64.getDecoder().decode(base64Content);

        if (mimeType != null && isTextMimeType(mimeType)) {
            return new String(decodedBytes, StandardCharsets.UTF_8);
        }

        return decodedBytes;
    }

    private static boolean isTextMimeType(String mimeType) {
        return mimeType.startsWith("text/") ||
                mimeType.equals("plain/text") ||
                mimeType.equals("application/json") ||
                mimeType.equals("application/xml") ||
                mimeType.equals("application/javascript");
    }

    public static String decodeBase64ToString(String text) {
        // TODO: Need to review the strategy when working with binary/image files, because the
        //  String will miss/corrupt important info about the bytes we decoded
        byte[] decodedBytes = decodeBase64(text);

        return new String(decodedBytes);
    }

    public static String combinedKey(Object... args) {
        return Arrays.stream(args).map(String::valueOf).collect(Collectors.joining("-"));
    }

    public static String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    public static List<Object> splitParams(String input) {
        List<Object> result = new ArrayList<>();
        if (input == null) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        char inQuotes = '\0';

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if ((inQuotes == '\0' || c == inQuotes) && (c == '"' || c == '\'') && (i == 0 || input.charAt(i - 1) != '\\')) {
                inQuotes = inQuotes == '\0' ? c : '\0';
            }

            if (c == ',' && inQuotes == '\0') {
                result.add(castValue(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(castValue(current.toString().trim()));
        }

        return result;
    }

    public static Object castValue(String input) {
        if (input == null || input.isEmpty()) return null;

        if (input.length() >= 2 &&
                (input.startsWith("\"") && input.endsWith("\"") || input.startsWith("'") && input.endsWith("'"))
        ) {
            return input.substring(1, input.length() - 1);
        }

        if (input.equalsIgnoreCase("null")) return null;

        if (input.equalsIgnoreCase("true")) return true;
        if (input.equalsIgnoreCase("false")) return false;

        try {
            if (input.contains(".")) return Double.parseDouble(input);
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
        }

        return input;
    }

    public static <T> List<List<T>> listGroupsOf(List<T> list, int groupSize) {
        return IntStream.range(0, list.size())
                .filter(i -> i % Math.max(groupSize, 1) == 0)
                .mapToObj(i -> list.subList(i, Math.min(i + groupSize, list.size())))
                .toList();
    }

    public static <T> T getParam(List<Object> params, int index, Object defaultValue) {
        if (params.size() > index) {
            return (T) params.get(index);
        }

        return (T) defaultValue;
    }

    public static List<Pair<?, ?>> zip(List<?> list1, List<?> list2) {
        if (list1 == null) {
            list1 = new ConcurrentLinkedList<>();
        }

        if (list2 == null) {
            list2 = new ConcurrentLinkedList<>();
        }

        List<Pair<?, ?>> pairs = new ConcurrentLinkedList<>();
        for (int i = 0; i < Math.max(list1.size(), list2.size()); i++) {
            Object val1 = null;
            if (i < list1.size()) {
                val1 = list1.get(i);
            }

            Object val2 = null;
            if (i < list2.size()) {
                val2 = list2.get(i);
            }

            pairs.add(new MutablePair<>(val1, val2));
        }

        return pairs;
    }

    public static List<Object> nonNullOfAnyList(List<?>... lists) {
        List<Object> newList = new ConcurrentLinkedList<>();

        int size = 0;
        for (var list : lists) {
            size = Math.max(size, list == null ? 0 : list.size());
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < lists.length; j++) {
                if (lists[j] instanceof List<?> currentList && currentList.size() > i) {
                    var currentListItemValue = currentList.get(i);
                    if (currentListItemValue != null) {
                        newList.add(currentListItemValue);
                        break;
                    }
                }
            }

            if (newList.size() == i) {
                newList.add(null);
            }
        }

        return newList;
    }

    /**
     * Builds a tree string from the given paths relative to a common base.
     *
     * @param rawPaths     List of absolute or relative file paths.
     * @param commonPrefix The base path to ignore in the tree rendering.
     * @return A tree-structured string representation.
     */
    public static String buildTreeString(List<String> rawPaths, String commonPrefix) {
        Path basePath = StringUtils.isBlank(commonPrefix) ? null : Paths.get(commonPrefix).normalize();
        Node root = new Node("", false);

        String top = "";

        for (String rawPath : rawPaths) {
            Path fullPath = Paths.get(rawPath).normalize();

            Path relativePath = fullPath;
            if (basePath != null) {
                if (!fullPath.startsWith(basePath)) {
                    System.err.println("Skipping path outside of base: " + fullPath);
                    continue;
                }

                relativePath = basePath.relativize(fullPath);
            }

            Node current = root;
            if (isEmpty(commonPrefix)) {
                top = Utils.nvl(relativePath.getRoot(), "") + "\n";
            }

            for (int i = 0; i < relativePath.getNameCount(); i++) {
                String part = relativePath.getName(i).toString();
                boolean isFile = (i == relativePath.getNameCount() - 1);
                current.children.putIfAbsent(part, new Node(part, isFile));
                current = current.children.get(part);
            }
        }

        StringBuilder sb = new StringBuilder(top);
        List<Node> children = new ArrayList<>(root.children.values());
        for (int i = 0; i < children.size(); i++) {
            buildString(children.get(i), sb, "", i == children.size() - 1);
        }
        return sb.toString();
    }

    private static void buildString(Node node, StringBuilder sb, String prefix, boolean isLast) {
        sb.append(prefix)
                .append(isLast ? "└── " : "├── ")
                .append(node.name);

        if (!node.isFile) {
            sb.append("/");
        }

        sb.append("\n");

        List<Node> children = new ArrayList<>(node.children.values());
        for (int i = 0; i < children.size(); i++) {
            buildString(children.get(i), sb, prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
        }
    }

    static class Node {
        String name;
        boolean isFile;
        Map<String, Node> children = new TreeMap<>();

        Node(String name, boolean isFile) {
            this.name = name;
            this.isFile = isFile;
        }
    }

    @SuppressWarnings("unchecked")
    public static void putAllMergeMaps(Map<String, Object> target, Map<String, Object> source, boolean shouldExistOnBoth) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();

            if (!shouldExistOnBoth || target.containsKey(key)) {
                Object sourceValue = entry.getValue();
                Object targetValue = target.get(key);

                if (targetValue instanceof Map && sourceValue instanceof Map) {
                    putAllMergeMaps((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue, false);
                } else {
                    target.put(key, sourceValue);
                }
            }
        }
    }

    public static String getEnvVariable(String envVariableName) {
        return System.getenv(envVariableName);
    }

    public static List<Map<String, String>> getAllRegexGroups(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        List<Map<String, String>> result = new ArrayList<>();

        Map<Integer, String> groupNames = getNamedGroups(regex);

        while (matcher.find()) {
            Map<String, String> matchMap = new LinkedHashMap<>();
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String value = matcher.group(i);
                String indexString = String.valueOf(i);
                matchMap.put(indexString, value);

                if (groupNames.containsKey(i)) {
                    String keyName = groupNames.get(i);
                    matchMap.put(keyName, value);
                }
            }
            result.add(matchMap);
        }

        return result;
    }

    public static Map<Integer, String> getNamedGroups(String regex) {
        Map<Integer, String> namedGroups = new HashMap<>();
        Pattern namedGroupPattern = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
        Matcher m = namedGroupPattern.matcher(regex);

        int index = 1;
        while (m.find()) {
            namedGroups.put(index++, m.group(1));
        }
        return namedGroups;
    }

    public static List<Object> filterByIndexes(List<Object> list, Object indexes) throws JsonProcessingException {
        List<Integer> indexesList;
        if (indexes instanceof String indexesString) {
            var from = indexesString.indexOf("[");
            var to = indexesString.indexOf("]") + 1;
            indexesString = indexesString.substring(from, to);
            indexesList = JsonUtils.readAsListOf(indexesString, Integer.class);
        } else {
            indexesList = (List<Integer>) indexes;
        }

        return IntStream.range(0, list.size())
                .filter(i -> !indexesList.contains(i))
                .mapToObj(list::get)
                .collect(Collectors.toList());
    }

    public static String detectLanguage(String text) {
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        LanguageResult result = detector.detect(text);
        return result.getLanguage();
    }

    public static void traverseJsonPath(Map<String, Object> output, Object obj, String path, Function<Object, Object> keyTransform) {
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                var rawKey = keyTransform == null ? entry.getKey() : keyTransform.apply(entry.getKey());
                if (!(rawKey instanceof String key)) {
                    throw new IllegalArgumentException("Only String keys are supported in JSONPath traversal");
                }

                String currentPath = path.isEmpty() ? key : path + "." + key;
                traverseJsonPath(output, entry.getValue(), currentPath, keyTransform);
            }
        } else if (obj instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object element = keyTransform == null ? list.get(i) : keyTransform.apply(list.get(i));
                String currentPath = path + "[" + i + "]";
                traverseJsonPath(output, element, currentPath, keyTransform);
            }
        } else {
            output.put(path, obj);
        }
    }

    public static String urlToKey(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String trimmed = url.trim().replaceAll("/+$", "");
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash > -1) {
            trimmed = trimmed.substring(lastSlash + 1);
        }

        var lower = trimmed.toLowerCase();
        var withoutExt = Utils.getRegexGroup(lower, "([a-z0-9]+)?(\\..*)?$", 1);
        assert withoutExt != null;
        var normalized = withoutExt.replaceAll("[^a-z0-9]", "-");

        return normalized + "-" + hashString(url).substring(0, 8);
    }

    public static String removeColumns(String input, int columnsToRemoveStart, int columnsToRemoveEnd) {
        String[] lines = input.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            if (line.length() - 1 < columnsToRemoveStart) {
                result.append("\n");
                continue;
            }

            int startRemoveCount = columnsToRemoveStart == -1 ? 0 : Math.min(columnsToRemoveStart, line.length());
            int endRemoveCount = Math.max(columnsToRemoveStart, Math.min(columnsToRemoveEnd, line.length()));

            String modifiedLine = line.substring(startRemoveCount, endRemoveCount);

            result.append(modifiedLine).append("\n");
        }

        if (result.length() > 0) {
            result.deleteCharAt(result.length() - 1);
        }

        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public static void replaceKeysWithMockValue(Object obj, String mockValue, Object... keysOrClassToReplace) {
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());

                boolean matched = false;
                for (Object targetRef : keysOrClassToReplace) {
                    if (targetRef instanceof String targetKey) {
                        if (targetKey.equals(key)) {
                            ((Map.Entry<Object, Object>) entry).setValue(mockValue);
                            matched = true;
                            break;
                        }
                    } else if (entry.getValue() != null && targetRef instanceof Class<?> targetClass && targetClass.isAssignableFrom(entry.getValue().getClass())) {
                        ((Map.Entry<Object, Object>) entry).setValue(mockValue);
                    }
                }

                if (!matched) {
                    replaceKeysWithMockValue(entry.getValue(), mockValue, keysOrClassToReplace);
                }
            }
        } else if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                replaceKeysWithMockValue(item, mockValue, keysOrClassToReplace);
            }
        }
    }

    public static Collection<Object> flatten(Collection<?> input) {
        Collection<Object> result = new ArrayList<>();
        for (Object o : input) {
            if (o instanceof Collection<?> nested) {
                result.addAll(flatten(nested));
            } else {
                result.add(o);
            }
        }
        return result;
    }

    public static Object flattenAny(Object input) {
        boolean[] containsMap = new boolean[1];
        Object result;

        if (input == null) {
            return null;
        }

        if (input.getClass().isArray()) {
            int length = Array.getLength(input);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(input, i));
            }
            input = list;
        }

        if (isOrContainsMap(input)) {
            containsMap[0] = true;
            Map<Object, Object> map = new ConcurrentLinkedHashMap<>();
            flattenToMap(input, map);
            result = map;
        } else {
            List<Object> list = new ConcurrentLinkedList<>();
            flattenToList(input, list);
            result = list;
        }

        return result;
    }

    private static boolean isOrContainsMap(Object input) {
        if (input instanceof Map<?, ?>) {
            return true;
        } else if (input instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (isOrContainsMap(item)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void flattenToList(Object input, List<Object> result) {
        if (input instanceof Collection<?> coll) {
            for (Object item : coll) {
                flattenToList(item, result);
            }
        } else if (input instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                flattenToList(value, result);
            }
        } else {
            result.add(input);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenToMap(Object input, Map<Object, Object> result) {
        if (input instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        } else if (input instanceof Collection<?> coll) {
            for (Object item : coll) {
                flattenToMap(item, result);
            }
        }
        // else: non-mappable scalar value — ignore or collect as numbered keys if desired
    }

    public static <T> List<T> safeSubList(List<T> list, int fromIndex) {
        if (list == null || list.size() <= fromIndex) {
            return Collections.emptyList();
        }
        return list.subList(fromIndex, list.size());
    }

    public static <T> T safeGet(Supplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}