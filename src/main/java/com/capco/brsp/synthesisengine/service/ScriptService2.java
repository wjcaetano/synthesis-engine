package com.capco.brsp.synthesisengine.service;

import ch.qos.logback.core.util.StringUtil;
import com.capco.brsp.synthesisengine.dto.*;
import com.capco.brsp.synthesisengine.configuration.PaginationConfig;
import com.capco.brsp.synthesisengine.dto.grammars.Grammar;
import com.capco.brsp.synthesisengine.exception.PythonException;
import com.capco.brsp.synthesisengine.exception.SpelEvaluationExceptionDetails;
import com.capco.brsp.synthesisengine.extractors.Extractors;
import com.capco.brsp.synthesisengine.tools.ToolsFunction;
import com.capco.brsp.synthesisengine.tools.Transforms;
import com.capco.brsp.synthesisengine.utils.*;
import com.github.javaparser.ast.body.FieldDeclaration;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.*;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import io.github.koinsaari.jtoon.Toon;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.json.Json;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Setter
@Service(value = "scriptService2")
public class ScriptService2 implements IScriptService {
    private static final Map<String, Map<String, Object>> TRANSFORM_CACHE = new ConcurrentLinkedHashMap<>();
    private static final Map<String, ITransform> CUSTOM_TRANSFORMS = new ConcurrentLinkedHashMap<>();
    private static final String SPEL_EXPRESSION_DELIMITER = "^\\s*\\$\\{([\\s\\S]+)}\\s*$";
    private static final Pattern PATTERN_SPEL_EXPRESSION = Pattern.compile(SPEL_EXPRESSION_DELIMITER);
    private final ApplicationContext applicationContext;
    @Autowired
    @Qualifier("llmSpringService")
    private LLMSpringService llmSpringService;
    @Autowired
    @Qualifier("agentRegistryService")
    private AgentRegistryService agentRegistryService;
    @Autowired
    @Qualifier("llmEmbeddingSpringService")
    private LLMEmbeddingSpringService llmEmbeddingSpringService;
    @Autowired
    @Qualifier("plantUMLService")
    private PlantUMLService plantUMLService;
    @Autowired
    @Qualifier("contextService")
    private ContextService contextService;
    @Autowired
    private ToolsFunction toolsFunction;
    @Autowired
    @Qualifier("agensGraphService")
    private AgensGraphService agensGraphService;

    @Qualifier("beanResolver")
    private final BeanResolver beanResolver;
    private final Configuration freemakerConfig = new Configuration(Configuration.getVersion());
    private final StringTemplateLoader freemarkerTemplateLoader = new StringTemplateLoader();
    private final ExpressionParser parser = new SpelExpressionParser();
    Map<String, IExecutor> executorsCache = new NullableConcurrentHashMap<>();

    private PythonWorker worker;

    public void removeCache(String flowKey, String cacheHash) {
        TRANSFORM_CACHE.get(flowKey).remove(cacheHash);
    }

    @PostConstruct
    private void postConstruct() throws TemplateModelException {
        freemakerConfig.setTemplateLoader(freemarkerTemplateLoader);

        // For production purposes
        freemakerConfig.setSharedVariable("SuperUtils", SuperUtils.getInstance());
        freemakerConfig.setSharedVariable("superService", applicationContext.getBean(SuperService.class));

        // For debugging purposes
        freemakerConfig.setSharedVariable("Utils", Utils.getInstance());
        freemakerConfig.setSharedVariable("scriptService", this);
    }

    public String evalSpELItems(String text) {
        Pattern pattern = Pattern.compile("\\$\\{(.*?)}\\$");
        Matcher matcher = pattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object value = evalSpEL(expression);
            matcher.appendReplacement(result, String.valueOf(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public StandardEvaluationContext getSpELContext(Map<String, Object> context) {
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext();

        var projectContext = Utils.nvl(context, new ConcurrentLinkedHashMap<>());
        standardEvaluationContext.setVariable("projectContext", projectContext);
        standardEvaluationContext.setVariables((Map<String, Object>) projectContext);
        standardEvaluationContext.setBeanResolver(beanResolver);

        return standardEvaluationContext;
    }

    @Override
    public <T> T evalIfSpEL(Object value) {
        if (value instanceof String str && isValidSpEL(str)) {
            return (T) evalSpEL(str);
        }
        return (T) value;
    }

    @Override
    public Object evalSpEL(String expression) {
        return evalSpEL(contextService.getProjectContext(), expression);
    }

    @Override
    public Object evalSpEL(Map<String, Object> context, String expression) {
        String expressionContent = getSpELContent(expression);

        var standardEvaluationContext = getSpELContext(context);

        try {
            return parser.parseExpression(expressionContent).getValue(standardEvaluationContext);
        } catch (SpelEvaluationException ex) {
            throw new SpelEvaluationExceptionDetails(expression, ex);
        } catch (Exception ex) {
            throw ex;
        }
    }

    public String getSpELContent(String expression) {
        if (!isValidSpEL(expression)) {
            throw new IllegalStateException("Not a valid single expression! Expression: " + expression);
        }

        var matcher = PATTERN_SPEL_EXPRESSION.matcher(expression);
        return matcher.results().findFirst().map(it -> it.group(1)).orElse(null);
    }

    @Override
    public boolean isValidSpEL(String expression) {
        if (StringUtil.isNullOrEmpty(expression)) {
            return false;
        }

        return expression.matches(SPEL_EXPRESSION_DELIMITER);
    }

    public boolean isValidJsonPath(String expression) {
        if (StringUtil.isNullOrEmpty(expression)) {
            return false;
        }

        return expression.startsWith("$.") || expression.startsWith("$[");
    }

    public Object evalSpELOrReturn(String expression) {
        if (isValidSpEL(expression)) {
            return evalSpEL(expression);
        }

        return expression;
    }

    public Object evalGroovyByShell(String groovyScript) {
        Binding binding = new Binding();
        binding.setVariable("applicationContext", applicationContext);
        binding.setVariable("projectContext", contextService.getProjectContext());

        GroovyShell shell = new GroovyShell(binding);
        return shell.evaluate(groovyScript);
    }

    // Breakpoint to debug groovy scripts
    public Object evalGroovy(String scriptContentOrFilePath, Object... params) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (looksLikeGroovySnippet(scriptContentOrFilePath) && !isExistingGroovyFile(scriptContentOrFilePath) && !scriptContentOrFilePath.contains("IExecutor")) {
            return evalGroovyByShell(scriptContentOrFilePath);
        }

        IExecutor groovyObject = getGroovyExecutor(scriptContentOrFilePath);

        if (params == null || params.length == 0) {
            return groovyObject.execute(applicationContext, contextService.getProjectContext());
        } else {
            return groovyObject.execute(applicationContext, contextService.getProjectContext(), params);
        }
    }

    public IExecutor getGroovyExecutor(String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final Class<IExecutor> groovyClass;

        String executorHashSignature = Utils.hashString(scriptContentOrFilePath);
        if (executorsCache.containsKey(executorHashSignature)) {
            return executorsCache.get(executorHashSignature);
        }

        // Use the current thread's classloader as the parent
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        GroovyClassLoader classLoader = new GroovyClassLoader(parent); // Do NOT close this

        boolean debugMode = Utils.isDebugMode();
        String executionMode = debugMode ? "DEBUG" : "PROD";

        if (scriptContentOrFilePath.contains("\n")) {
            log.warn("Execution mode: {}. Loading Groovy from inline recipe body.", executionMode);
            groovyClass = classLoader.parseClass(scriptContentOrFilePath);
        } else {
            log.warn("Execution mode: {}. Loading Groovy from file: {}", executionMode, scriptContentOrFilePath);
            groovyClass = classLoader.parseClass(new File(scriptContentOrFilePath));
        }

        var newInstance = groovyClass.getDeclaredConstructor().newInstance();

        executorsCache.put(executorHashSignature, newInstance);

        return newInstance;
    }

    private static boolean looksLikeGroovySnippet(String s) {
        if (s == null) return false;
        String t = s.trim();

        if (t.contains("\n")) return true;

        if (t.matches(".*[(){};=+\\-*/\"'`$].*")) return true;

        String lower = t.toLowerCase();
        if (lower.contains("def ") || lower.contains("class ")
                || lower.startsWith("return ") || lower.startsWith("import ")
                || lower.startsWith("println") || lower.contains(" new ")
                || lower.startsWith("//") || lower.startsWith("/*")) {
            return true;
        }

        return false;
    }

    private static boolean isExistingGroovyFile(String s) {
        if (s == null || s.isBlank()) return false;

        boolean pathish = s.contains("/") || s.contains("\\")
                || s.endsWith(".groovy") || s.endsWith(".gvy") || s.endsWith(".gy");

        if (!pathish) return false;

        try {
            Path p = Paths.get(s);
            return Files.isRegularFile(p) && Files.isReadable(p);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    public ITransform getGroovyTransform(String transformName, String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final Class<ITransform> groovyClass;

        try (GroovyClassLoader classLoader = new GroovyClassLoader()) {
            if (scriptContentOrFilePath.contains("\n")) {
                groovyClass = classLoader.parseClass(scriptContentOrFilePath);
            } else {
                groovyClass = classLoader.parseClass(new File("src\\main\\resources\\transforms\\" + scriptContentOrFilePath));
            }
        }

        var newInstance = groovyClass.getDeclaredConstructor().newInstance();

        CUSTOM_TRANSFORMS.put(transformName, newInstance);

        return newInstance;
    }

    public String evalFreemarker(String content) {
        Map<String, Object> context = contextService.getProjectContext();

        try {
            return evalFreemarker(context, content);
        } catch (Exception ex) {
            log.error("Error trying to evaluate a Freemarker template. Error: {}", ex.getMessage());
            ex.printStackTrace();

            return content;
        }
    }

    public String evalFreemarker(Map<String, Object> context, String content) throws IOException, TemplateException {
        String templateName = UUID.randomUUID().toString();

        if (content == null) {
            return null;
        }

        try (StringWriter writer = new StringWriter()) {
            freemarkerTemplateLoader.putTemplate(templateName, content);

            Template template = freemakerConfig.getTemplate(templateName);
            template.process(context, writer);

            return writer.toString();
        } finally {
            freemarkerTemplateLoader.removeTemplate(templateName);
        }
    }

    public String evalFreemarker(String name, String content) throws IOException, TemplateException {
        Template template;
        try {
            template = freemakerConfig.getTemplate(name);
        } catch (TemplateNotFoundException ex) {
            freemarkerTemplateLoader.putTemplate(name, content);
            template = freemakerConfig.getTemplate(name);
        }

        StringWriter writer = new StringWriter();
        template.process(contextService.getProjectContext(), writer);

        return writer.toString();
    }

    public Object autoEvalExpression(String value) {
        return autoEvalExpression(value, new ConcurrentLinkedList<>());
    }

    public Object autoEvalExpression(String value, List<TransformDto> history) {
        long start = System.currentTimeMillis();

        while (isValidSpEL(value)) {
            var evaluated = evalSpEL(value);
            long end = System.currentTimeMillis();
            addHistory(history, "SpEL", value, end - start);
            if (evaluated instanceof String evaluatedString) {
                value = evaluatedString;
            } else {
                return evaluated;
            }
        }

        return value;
    }

    public Object splitContentAndPopulateTransforms(String content, List<TransformDto> history, Map<String, Object> projectContext, List<TransformDto> transforms) {
        boolean foundFirstNonAtLine = false;
        List<String> transformLines = new ConcurrentLinkedList<>();
        List<String> remainingContent = new ConcurrentLinkedList<>();
        List<String> lines = content.lines().toList();
        for (String line : lines) {
            if (!foundFirstNonAtLine) {
                var strippedLine = line.stripLeading();
                if (strippedLine.startsWith("@@@")) {
                    transformLines.add(line);
                } else if (!line.isBlank()) {
                    foundFirstNonAtLine = true;
                    remainingContent.add(line);
                }
            } else {
                remainingContent.add(line);
            }
        }

        var transformLines2 = new ConcurrentLinkedList<String>(transformLines);
        if (projectContext.get("recipe") instanceof Map<?, ?> recipeMap && recipeMap.get("macros") instanceof Map<?, ?> macrosMap) {
            transformLines.clear();
            for (var transformLine : transformLines2) {
                var macro = macrosMap.entrySet().stream().filter(it -> it.getKey().equals(transformLine.split("@@@")[1])).findFirst().orElse(null);
                if (macro != null) {
                    transformLines.addAll(((String) macro.getValue()).lines().toList());
                } else {
                    transformLines.add(transformLine);
                }
            }
        }

        String newContent = String.join("\n", remainingContent);
        if (transformLines.isEmpty()) {
            var transformDto = TransformDto.builder().sentence("<EMPTY>").name("Static Content").content(newContent).build();
            history.add(transformDto);
        }

        for (var transformLine : transformLines) {
            var transformCleaned = Objects.requireNonNull(transformLine).trim().substring(3).trim();
            var transformCommand = Utils.getTextBefore(transformCleaned, "\\(");
            var transformParams = Utils.getRegexGroup(transformCleaned, "^\\w+\\((.*)\\)$", 1);

            var parsedTransformParams = Utils.splitParams(transformParams);
            var transformDefaultParams = Utils.anyCollectionGet(projectContext, "recipe.config.transformDefaultParams." + transformCommand);
            if (transformDefaultParams instanceof List<?> transformDefaultParamsList) {
                parsedTransformParams = Utils.nonNullOfAnyList(parsedTransformParams, transformDefaultParamsList);
            }

            boolean dontUpdateContent = false;
            if (transformCommand.startsWith("_")) {
                transformCommand = transformCommand.substring(1);
                dontUpdateContent = true;
            }

            var transformDto = TransformDto.builder().sentence(transformLine).name(transformCommand).command(transformCommand.toLowerCase()).parameters(parsedTransformParams).update(!dontUpdateContent).originalTransformParams(transformParams).build();
            transforms.add(transformDto);
        }

        return newContent;
    }

    public Object autoEvalStringTransforms(String content) throws Exception {
        return autoEvalStringTransforms(content, new ConcurrentLinkedList<>());
    }

    public Object autoEvalStringTransforms(String content, List<TransformDto> history) throws Exception {
        return autoEvalStringTransforms(content, history, 0);
    }

    public Object autoEvalStringTransforms(String content, List<TransformDto> history, int startIndex) throws Exception {
        // ############ IMPORTANT NOTES ##############
        // @set was removed!

        var flowKey = contextService.getFlowKey();
        var projectContext = contextService.getProjectContext();

        List<TransformDto> transforms = new ConcurrentLinkedList<>();
        Object newContent = splitContentAndPopulateTransforms(content, history, projectContext, transforms);
        projectContext.put("content", newContent);

        String defaultExpression = null;
        Object retryCheckpointContent = null;
        String retryCheckpointExpression = "";
        int retryCheckpointIndex = -1;

        String cacheHash = null;
        String cachePath = null;
        List<Object> cacheDataList = new ConcurrentLinkedList<>();

        boolean checkpointRecovered = false;
        int attempts = -1;
        int transformIndex = startIndex;

        Map<String, CheckpointDto> checkpointMap = new ConcurrentLinkedHashMap<>();

        Map<String, Object> tempCache = new ConcurrentLinkedHashMap<>();
        if (TRANSFORM_CACHE.get(flowKey) == null) {
            TRANSFORM_CACHE.put(flowKey, new ConcurrentLinkedHashMap<>());
        }
        var flowCache = TRANSFORM_CACHE.get(flowKey);

        LB_WHILE:
        while (transformIndex < transforms.size()) {
            checkpointRecovered = false;

            long start = System.currentTimeMillis();

            var transform = transforms.get(transformIndex++);
            var transformCommand = transform.getCommand();
            var transformParameters = transform.getParameters();

            history.add(transform);

            String serializedData = "<UNSERIALIZABLE DATA>";
            if (newContent instanceof String newContentStr) {
                serializedData = newContentStr;
            } else {
                try {
                    serializedData = JsonUtils.writeAsJsonStringCircular(newContent, true, true);
                } catch (Exception ex) {
                    var jsonException = JsonUtils.throwableAsJson(ex);
                    serializedData += "\n" + jsonException;
                }
            }
            String historyContent = String.join("\n", Utils.safeSubList(transforms, transformIndex - 1).stream().map(TransformDto::getSentence).toList()) + "\n" + serializedData;
            transform.setContent(historyContent);

            Object backupContent = newContent;

            projectContext.entrySet().removeIf(it -> it.getKey().startsWith("_arg"));
            for (int i = 0; i < transformParameters.size(); i++) {
                projectContext.put("_arg" + i, transformParameters.get(i));
            }

            log.debug("Command: {}", transformCommand);

            var isCacheable = projectContext.get("recipe") instanceof Map<?, ?> recipeMap
                    && recipeMap.get("caches") instanceof Map<?, ?> cachesMap
                    && cachesMap.get("transforms") instanceof List<?> cachesTransformList
                    && cachesTransformList.contains(transformCommand.toLowerCase());
            var transformCacheHash = Utils.hashString(transformParameters.stream().map(String::valueOf).collect(Collectors.joining()) + (newContent instanceof String newContentString ? newContentString : JsonUtils.writeAsJsonStringCircular(newContent, true, false)));

            if (isCacheable && flowCache.get(transformCacheHash) instanceof String cachedContent) {
                log.info("Restored by cache!");
                newContent = cachedContent;
            } else {
                try {
//                var tool = toolsFunction.getToolDto(transformCommand, transformParameters, newContent);
//                newContent = toolsFunction.invokeToolMethod(tool);
                    switch (transformCommand) {
                        case "failif":
                            Object failIfExpression = transformParameters.getFirst();

                            String exceptionMessage = null;
                            if (transformParameters.size() > 1) {
                                var evaluated = evalIfSpEL(transformParameters.get(1));
                                exceptionMessage = evaluated instanceof String evaluatedString ? evaluatedString : String.valueOf(evaluated);
                            }
                            var failIfResult = evalIfSpEL(failIfExpression);
                            if (Boolean.TRUE.equals(failIfResult)) {
                                if (exceptionMessage != null) {
                                    log.error(exceptionMessage);
                                }

                                log.error("Failed based on the condition: {} which evaluated to {}", failIfExpression, failIfResult);
                                throw new RuntimeException("Failed based on the condition: " + failIfExpression);
                            }
                            break;

                        case "retry":
                            log.warn("The transformAction '{}' will be deprecated soon!", transformCommand);
                            attempts = (Integer) transformParameters.getFirst();
                            retryCheckpointExpression = transformParameters.size() > 1 ? (String) transformParameters.get(1) : "";

                            retryCheckpointIndex = transformIndex;
                            retryCheckpointContent = newContent;
                            break;

                        case "skip":
                            log.warn("The transformAction '{}' will be deprecated soon!", transformCommand);
                            String skipExpression = String.valueOf(transformParameters.getFirst());
                            var isSkip = Utils.castOrDefault(evalSpEL(skipExpression), Boolean.class, Boolean.FALSE);
                            if (isSkip) {
                                if (transform.isUpdate()) {
                                    newContent = "// Skipped/Empty file";
                                }
                                break LB_WHILE;
                            }
                            break;

                        case "case":
                            log.warn("The transformAction '{}' will be deprecated soon!", transformCommand);
                            var caseParamsPairs = Utils.listGroupsOf(transformParameters, 2);

                            for (var caseParamsPair : caseParamsPairs) {
                                var caseParamsPairConditionOrDefault = Utils.getParam(caseParamsPair, 0, null);

                                if (caseParamsPair.size() == 1) {
                                    newContent = autoEval((String) caseParamsPairConditionOrDefault, history);
                                    break;
                                }

                                String caseParamsPairTemplate = Utils.getParam(caseParamsPair, 1, "");

                                if ((boolean) autoEval((String) caseParamsPairConditionOrDefault)) {
                                    newContent = autoEval(caseParamsPairTemplate, history);
                                    break;
                                }
                            }
                            break;

                        case "exec":
                            log.warn("The transformAction '{}' will be deprecated soon!", transformCommand);
                            String template = (String) transformParameters.getFirst();
                            if (isValidSpEL(template)) {
                                template = String.valueOf(evalSpEL(template));
                            } else {
                                log.warn("Raw template will be executed, as the parameter was not identified as a valid SpEL content!");
                            }

                            newContent = autoEval(template, history);
                            break;

                        case "messages":
                            var flow = contextService.getFlow(flowKey);
                            var rotateMessages = new ConcurrentLinkedList<String>();
                            flow.getCurrentTask().setRotateMessages(rotateMessages);

                            transformParameters.forEach(tp -> {
                                rotateMessages.add(evalIfSpEL(tp));
                            });
                            break;

                        case "log":
                            log.warn("The transformAction '{}' require a better way to express/control/position inside the recipe!", transformCommand);
                            String logExpression = Utils.getParam(transformParameters, 0, null);
                            String logMessage = evalIfSpEL(logExpression);
                            for (var match : Utils.getAllRegexGroups(logMessage, "(?<hexColor>#[A-F0-9]{6})")) {
                                var hexColor = match.get("hexColor");
                                int rgb = Integer.decode(hexColor);
                                int r = (rgb >> 16) & 0xFF;
                                int g = (rgb >> 8)  & 0xFF;
                                int b = rgb & 0xFF;

                                logMessage = logMessage.replace(hexColor, "\u001B[38;2;" + r +";" + g + ";" + b + "m");
                            }

                            if (logMessage.contains("\u001B[")) {
                                logMessage += "\u001B[0m";
                            }

                            log.info(logMessage);
                            break;

                        case "recipe":
                            throw new UnsupportedOperationException("@@@recipe still waiting to be implemented!");

                        case "ask":
                            String key = evalIfSpEL(transformParameters.getFirst());
                            String question = evalIfSpEL(Utils.getParam(transformParameters, 1, null));

                            List<Map<String, Object>> askQueue = (List<Map<String, Object>>) projectContext.computeIfAbsent("askQueue", _ -> new ConcurrentLinkedList<Map<String, Object>>());
                            Map<String, Object> ask = new ConcurrentLinkedHashMap<>();
                            askQueue.add(ask);

                            ask.put("key", key);
                            ask.put("type", transformParameters.size() < 3 ? "INPUT" : "BUTTONS");
                            ask.put("label", question);
                            List<String> askValues = (List<String>) ask.computeIfAbsent("values", k -> new ConcurrentLinkedList<String>());

                            for (int i = 2; i < transformParameters.size(); i++) {
                                askValues.add(evalIfSpEL(Utils.getParam(transformParameters, i, null)));
                            }

                            projectContext.put("askQueue", askQueue);

                            log.warn("Waiting 30s for a User response!");
                            int askTimeoutCounter = 0;
                            while (askTimeoutCounter++ < 30 && projectContext.get("askQueue") instanceof List<?> nextAskQueue && !nextAskQueue.isEmpty()) {
                                Thread.sleep(1000);
                            }

                            if (askTimeoutCounter >= 30) {
                                projectContext.put("askQueue", new ConcurrentLinkedHashMap<>());
                                log.error("Ask Timeout!");
                                throw new RuntimeException("Ask Timeout!");
                            }
                            break;

                        case "repeat":
                            log.warn("The transformAction '{}' require a better way to express/control/position inside the recipe!", transformCommand);
                            Object repeatItems = evalIfSpEL(transformParameters.getFirst());
                            String repeatItemPath = Utils.getParam(transformParameters, 1, null);
                            String repeatTemplate = Utils.getParam(transformParameters, 2, null);

                            int repeatItemIndex = 0;
                            switch (repeatItems) {
                                case Map<?, ?> repeatItemsMap -> {
                                    for (var entry : repeatItemsMap.entrySet()) {
                                        Utils.anyCollectionSet(projectContext, repeatItemPath, entry);
                                        autoEval(repeatTemplate, history);
                                        projectContext.put("repeatItemIndex", repeatItemIndex++);
                                    }
                                }
                                case List<?> repeatItemsList -> {
                                    for (var item : repeatItemsList) {
                                        Utils.anyCollectionSet(projectContext, repeatItemPath, item);
                                        autoEval(repeatTemplate, history);
                                        projectContext.put("repeatItemIndex", repeatItemIndex++);
                                    }
                                }
                                case Integer repeatItemsInteger -> {
                                    for (int i = 0; i < repeatItemsInteger; i++) {
                                        Utils.anyCollectionSet(projectContext, repeatItemPath, repeatItems);
                                        autoEval(repeatTemplate, history);
                                        projectContext.put("repeatItemIndex", repeatItemIndex++);
                                    }
                                }
                                case null, default -> {
                                    Utils.anyCollectionSet(projectContext, repeatItemPath, repeatItems);
                                    autoEval(repeatTemplate, history);
                                    projectContext.put("repeatItemIndex", repeatItemIndex++);
                                }
                            }

                            projectContext.remove("repeatItemIndex");
                            break;

                        case "cache":
                            if (!projectContext.containsKey("cacheMap")) {
                                projectContext.put("cacheMap", new ConcurrentLinkedHashMap<>());
                            }

                            Map<String, Object> cacheMap = (ConcurrentLinkedHashMap<String, Object>) projectContext.get("cacheMap");
                            cachePath = (String) projectContext.get("filePath");
                            cacheHash = Utils.hashString(content + newContent);
                            if (cacheMap.get(cachePath) instanceof Map<?, ?> cacheItemMap && cacheItemMap.get("hash") instanceof String cacheItemHash && cacheItemHash.equals(cacheHash)) {
                                newContent = cacheItemMap.get("content");
                                if (cacheItemMap.get("vars") instanceof Map<?, ?> cacheVars) {
                                    var cacheVarsMap = (Map<String, Object>) cacheVars;
                                    cacheVarsMap.forEach((k, v) -> {
                                        Utils.anyCollectionSet(projectContext, k, v);
                                    });
                                }
                                transformIndex = transforms.size();
                            } else {
                                cacheDataList.addAll(transformParameters);
                            }
                            break;
                        case "fill":
                            Assert.isInstanceOf(String.class, newContent);

                            // Some expression that retrieve a list of items... It expects a Map, where each field is the original placeholder and
                            // The value can be anything... fillMatch: fillContext
                            // Call the template specified on the second parameter that should return the value to be used in place of the original match
                            String fillItemsExpression = Utils.getParam(transformParameters, 0, null);
                            String fillTemplateForEachItem = Utils.getParam(transformParameters, 1, null);

                            Objects.requireNonNull(fillItemsExpression, "fillItemsExpression should not be null!");
                            Objects.requireNonNull(fillTemplateForEachItem, "fillTemplateForEachItem should not be null!");

                            Map<String, Object> fillItems = (Map<String, Object>) autoEval(fillItemsExpression);

                            for (var fillItem : fillItems.entrySet()) {
                                var fillMatch = fillItem.getKey();
                                var fillContext = fillItem.getValue();

                                String filledValue = (String) ContextUtils.withTemporaryContext(
                                        projectContext,
                                        Map.of(
                                                "fillItem", fillItem,
                                                "fillMatch", fillMatch,
                                                "fillContext", fillContext
                                        ),
                                        () -> autoEval(fillTemplateForEachItem, history)
                                );

                                var replacedNewContent = ((String) newContent).replace(fillMatch, filledValue);
                                newContent = replacedNewContent;
                            }
                            break;

                        case "default":
                            defaultExpression = (String) transformParameters.getFirst();
                            break;

                        case "freemarker":
                            newContent = evalFreemarker(String.valueOf(newContent));
                            break;

                        case "spel":
                            String expression = !transformParameters.isEmpty() ? (String) transformParameters.getFirst() : (newContent instanceof String newContentString ? newContentString : null);

                            Map<String, Object> tempContext = new ConcurrentLinkedHashMap<>();
                            tempContext.put("projectContext", projectContext);
                            tempContext.putAll(projectContext);

                            newContent = evalSpEL(tempContext, expression);
                            break;

                        case "extractmarkdowncode":
                            newContent = Utils.extractMarkdownCode(newContent);
                            break;

                        case "schema":
                            String schema = evalIfSpEL(Utils.getParam(transformParameters, 0, null));
                            String errorsTarget = evalIfSpEL(Utils.getParam(transformParameters, 1, null));
                            boolean throwException = evalIfSpEL(Utils.getParam(transformParameters, 2, false));

                            var schemaErrors = JsonUtils.getErrorsAgainstJsonSchema(newContent, schema);
                            newContent = String.join("\n", schemaErrors);

                            if (!schemaErrors.isEmpty()) {
                                if (errorsTarget != null) {
                                    Utils.anyCollectionSet(projectContext, errorsTarget, schemaErrors);
                                }

                                if (throwException) {
                                    throw new RuntimeException("Failed to validated the schema:\n" + newContent);
                                }
                            }
                            break;

                        case "script":
                            var scriptsString = JsonUtils.writeAsJsonString(Utils.anyCollectionGet(projectContext, "recipe.scripts"), true, false);
                            Map<String, Script> scripts = JsonUtils.readAsMap(scriptsString, Script.class);

                            String scriptContent;
                            String scriptName = (String) transformParameters.getFirst();
                            if (scripts.get(scriptName) instanceof Script script) {
                                script.setName(scriptName);
                                if (Utils.isDebugMode() && script.getReference() != null) {
                                    var groovyFileName = evalIfSpEL(script.getReference());

                                    var scriptPath = FileUtils.pathJoin("src/main/resources/executors/", groovyFileName);
                                    if (!Files.exists(scriptPath)) {
                                        scriptPath = FileUtils.pathJoin("src/main/resources/transforms/", groovyFileName);
                                        if (!Files.exists(scriptPath)) {
                                            scriptPath = FileUtils.pathJoin("src/main/resources/scripts/", groovyFileName);
                                        }
                                    }

                                    scriptContent = Files.readString(scriptPath);
                                } else {
                                    scriptContent = evalIfSpEL(script.getBody());
                                }

                                // ensure to have a map and create a copy before solving the values
                                Map<String, Object> injectMap = (Map<String, Object>) Utils.convertToConcurrent(Utils.nvl(script.getInject(), new ConcurrentHashMap<>()));
                                injectMap.replaceAll((k, v) -> evalIfSpEL(v));

                                newContent = switch (script.getType()) {
                                    case GROOVY ->
                                            evalGroovy(scriptContent, Utils.safeSubList(transformParameters, 1).toArray(new Object[0]));

                                    case PYTHON -> {
                                            var pythonReturnAndVars = python(scriptContent, injectMap);

                                            if (script.getExtract() != null) {
                                                script.getExtract().forEach((k, v) -> Utils.anyCollectionSet(projectContext, evalIfSpEL(v), Utils.anyCollectionGet(pythonReturnAndVars, "vars." + k)));
                                            }

                                            yield pythonReturnAndVars.get("return");
                                    }

                                    default ->
                                            throw new UnsupportedOperationException("Script of type '" + script.getType() + " doesn't implemented yet!");
                                };
                            } else {
                                throw new IllegalStateException("Didn't found a script with the name '" + scriptName + "'!");
                            }
                            break;

                        case "python":
                            var pythonReturnAndVars = python((String) newContent, Map.of());
                            newContent = pythonReturnAndVars.get("return");
                            break;

                        case "groovy":
                            String groovyScriptPath = null;
                            if (Utils.isDebugMode() && !transformParameters.isEmpty()) {
                                String groovyFileName = (String) transformParameters.getFirst();
                                var groovyScriptRelativePath = FileUtils.pathJoin("src/main/resources/executors/", groovyFileName);
                                if (!Files.exists(groovyScriptRelativePath)) {
                                    groovyScriptRelativePath = FileUtils.pathJoin("src/main/resources/transforms/", groovyFileName);
                                    if (!Files.exists(groovyScriptRelativePath)) {
                                        groovyScriptRelativePath = FileUtils.pathJoin("src/main/resources/scripts/", groovyFileName);
                                    }
                                }
                                if (Files.exists(groovyScriptRelativePath)) {
                                    groovyScriptPath = groovyScriptRelativePath.toString();
                                }
                            }

                            newContent = evalGroovy((String) Utils.nvl(groovyScriptPath, newContent), Utils.safeSubList(transformParameters, 1).toArray(new Object[0]));
                            break;

                        case "jsonify":
                            newContent = JsonUtils.writeAsJsonStringCircular(newContent, true, false);
                            break;

                        case "toonify":
                            newContent = Toon.encode(newContent);
                            break;

                        case "yamlify":
                            newContent = YamlUtils.writeAsString(newContent);
                            break;

                        case "xmlify":
                            newContent = XmlUtils.writeAsXmlString(newContent, true);
                            break;

                        case "set":
                            String setPath = evalIfSpEL(transformParameters.getFirst());
                            String setObjectExpression = Utils.getParam(transformParameters, 1, null);

                            Object setObject = newContent;
                            if (setObjectExpression != null) {
                                setObject = autoEval(setObjectExpression);
                            }

                            Utils.anyCollectionSet(projectContext, setPath, setObject);
                            break;

                        case "get":
                            String getPath = (String) transformParameters.getFirst();
                            if (!isValidJsonPath(getPath) && !isValidSpEL(getPath) && !getPath.startsWith("@@@")) {
                                getPath = "$." + getPath;
                            }

                            newContent = autoEval(getPath);
                            break;

                        case "parse":
                            String parserLanguageString = Utils.getParam(transformParameters, 0, null);

                            newContent = ParserUtils.parse((Map<String, Grammar>) projectContext.get("grammars"), parserLanguageString, (String) newContent, Utils.safeSubList(transformParameters, 1).toArray(new String[0]));
                            break;

                        case "nodify":
                            newContent = Transforms.nodify(newContent);
                            break;

                        case "extract":
                            newContent = Extractors.extract(this, transformParameters, (String) newContent, projectContext);
                            break;

                        case "jolt":
                            String spec = (String) autoEval((String) transformParameters.getFirst());
                            newContent = toolsFunction.jolt(newContent, spec);
                            break;

                        case "openllmthread":
                            llmSpringService.startChat();
                            projectContext.put(CONSTANT_USE_LLM_THREAD, true);
                            projectContext.put(CONSTANT_LLM_THREAD_KEY, null);
                            break;

                        case "closellmthread":
                            var llmThreadKey1 = Utils.castOrDefault(projectContext.get(CONSTANT_LLM_THREAD_KEY), String.class, null);
                            if (llmThreadKey1 == null) {
                                log.warn("None threadKey was found for executing the '@@@closellmthread'");
                            } else {
                                llmSpringService.endChat();
                            }
                            projectContext.remove(CONSTANT_USE_LLM_THREAD);
                            projectContext.remove(CONSTANT_LLM_THREAD_KEY);
                            break;

                        case "mcp":
                            String mcpSubCommand = Utils.castOrDefault(Utils.getParam(transformParameters, 0, null), String.class, null);
                            if (mcpSubCommand == null) {
                                throw new IllegalStateException("@@@mcp requires a sub-command, e.g. @@@mcp(\"monolith-decomposition\")");
                            }
                            if ("monolith-decomposition".equalsIgnoreCase(mcpSubCommand)) {
                                Map<String, Object> filesCtx = llmSpringService.getFilesFromContext(projectContext);
                                if (filesCtx == null) {
                                    throw new IllegalStateException("No files found in the context");
                                }
                                Object rawDataCsvObj = filesCtx.get("data_matrix.csv");
                                Object rawAdjCsvObj = filesCtx.get("adjacency_matrix.csv");
                                Object rawSvgObj = filesCtx.get("graph_performs_calls.svg");
                                if (rawDataCsvObj == null || rawAdjCsvObj == null || rawSvgObj == null) {
                                    throw new IllegalStateException("Required files (data_matrix.csv, adjacency_matrix.csv, graph_performs_calls.svg) are missing to process the monolith decomposition.");
                                }

                                var mcpService = applicationContext.getBean(com.capco.brsp.synthesisengine.mcp.MonolithDecompositionMcpService.class);
                                String dataCsv = mcpService.normalizeFileText(String.valueOf(rawDataCsvObj));
                                String adjCsv = mcpService.normalizeFileText(String.valueOf(rawAdjCsvObj));
                                String svg = mcpService.normalizeFileText(String.valueOf(rawSvgObj));

                                String uid = UUID.randomUUID().toString();
                                var facade = applicationContext.getBean(com.capco.brsp.synthesisengine.mcp.MonolithDecompositionFacade.class);
                                var promptDto = facade.prepare(dataCsv, adjCsv, svg, uid);

                                Map<String, Object> monolith = projectContext.get("monolith") instanceof Map<?, ?> m ? (Map<String, Object>) m : new ConcurrentLinkedHashMap<>();
                                monolith.put("inputs", promptDto.getInputs());
                                monolith.put("uid", uid);
                                projectContext.put("monolith", monolith);
                                projectContext.put("monolith.uid", uid);

                                String promptWithUid = mcpService.buildMonolithPrompt(promptDto.getInputs(), uid);
                                projectContext.put("monolith.prompt", promptWithUid);

                                String agentNameParam = Utils.castOrDefault(Utils.getParam(transformParameters, 1, null), String.class, null);
                                AgentDto agentDto = null;

                                if (agentNameParam != null && !agentNameParam.isBlank()) {
                                    String target = agentNameParam.trim();

                                    Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");
                                    if (recipe != null && recipe.get("config") instanceof Map<?, ?> config && (Map<?, ?>) config.get("agents") instanceof List<?> agents) {
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> agentsList = (List<Map<String, Object>>) ((Map<?,?>) config).get("agents");
                                        Map<String, Object> selected = null;
                                        for (Map<String, Object> agentList : agentsList) {
                                            Object nameObj = agentList.get("name");
                                            if (nameObj != null && String.valueOf(nameObj).equalsIgnoreCase(target)) {
                                                selected = agentList;
                                                break;
                                            }
                                        }
                                        if (selected != null) {
                                            agentDto = llmSpringService.parseAgentConfig(selected);
                                            agentRegistryService.registerOrMerge(agentDto);
                                            log.info("MCP: Using agent '{}' merged from recipe by name '{}'", agentDto.getName(), agentNameParam);
                                        }
                                    }
                                }

                                if (agentDto == null && agentNameParam != null && agentNameParam.isBlank()) {
                                    agentDto = agentRegistryService.find(agentNameParam.trim()).orElse(null);
                                    if (agentDto != null) {
                                        log.info("MCP: Using agent '{}' resolved from registry by name '{}'", agentDto.getName(), agentNameParam);
                                    }
                                }

                                if (agentDto == null) {
                                    agentDto = llmSpringService.getDefaultConfig();
                                    log.warn("MCP: Agent '{}' not found in registry or recipe. Using default agent '{}'.",
                                            agentNameParam, agentDto.getName() != null ? agentDto.getName() : agentDto.getProvider());
                                }

                                Map<String, Object> meta = agentDto.getMetadata() instanceof Map<?, ?> mm ? (Map<String, Object>) mm : new ConcurrentLinkedHashMap<>();
                                meta.put("inputMode", "url-only");
                                agentDto.setMetadata(meta);
                                projectContext.put("agent", agentDto);

                                String agentResponse = handleAgent(projectContext, promptWithUid, null);

                                if (!agentResponse.matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*")) {
                                    projectContext.put("content", uid);
                                } else {
                                    projectContext.put("content", agentResponse);
                                }

                                var followUp = applicationContext.getBean(com.capco.brsp.synthesisengine.mcp.MonolithDecompositionFollowUpService.class);
                                String result = followUp.followUp(projectContext);

                                newContent = "MCP Monolith Decomposition job started. Result: " + result;
                            } else {
                                throw new IllegalStateException("Unsupported MCP sub-command: " + mcpSubCommand);
                            }
                            break;

                        case "prompt":
                            //llmSpringService.ensureLlmThreadActive(projectContext, "prompt");
                            newContent = handleAgent(projectContext, (String) newContent, null);
                            break;

                        case "agent":
                            Map<String, Object> agentParams = llmSpringService.parseAgentParams(transformParameters);
                            String agentName = (String) agentParams.get("agentName");
                            @SuppressWarnings("unchecked")
                            List<String> fileNames = (List<String>) agentParams.get("fileNames");
                            @SuppressWarnings("unchecked")
                            List<String> requestedTools = (List<String>) agentParams.get("requestedTools");

                            AgentDto agentDto = null;

                            @SuppressWarnings("unchecked")
                            Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");
                            Map<String, Object> config = (recipe != null) ? (Map<String, Object>) recipe.get("config") : null;

                            Map<String, Object> recipeAgentMap = null;
                            if (config != null && config.get("agents") instanceof List<?>) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> agents = (List<Map<String, Object>>) config.get("agents");
                                recipeAgentMap = agents.stream()
                                        .filter(it -> agentName != null && agentName.equalsIgnoreCase(String.valueOf(it.get("name"))))
                                        .findFirst()
                                        .orElse(null);
                            } else if (config != null && config.get("agent") instanceof Map<?,?> single) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> singleMap = (Map<String, Object>) single;
                                Object n = singleMap.get("name");
                                if (n != null && agentName != null && agentName.equalsIgnoreCase(String.valueOf(n))) {
                                    recipeAgentMap = singleMap;
                                }
                            }

                            if (recipeAgentMap != null) {
                                Object parsed = (recipeAgentMap.get("isEmbedding") instanceof Boolean b && b)
                                        ? llmSpringService.parseEmbeddingAgentConfig(recipeAgentMap)
                                        : llmSpringService.parseAgentConfig(recipeAgentMap);

                                if (parsed instanceof AgentDto dto) {
                                    agentDto = dto;
                                    agentRegistryService.registerOrMerge(agentDto);
                                    log.info("Agent: Using '{}' merged from recipe overrides for name '{}'", agentDto.getName(), agentName);
                                } else if (parsed instanceof AgentEmbConfigDto) {
                                    Object originalAgent = projectContext.get("agent");
                                    llmSpringService.ensureLlmThreadActive(projectContext, agentName != null ? agentName : "agent");
                                    try {
                                        projectContext.put("agent", parsed);
                                        handleAgent(projectContext, newContent, fileNames);
                                    } finally {
                                        if (originalAgent != null) projectContext.put("agent", originalAgent);
                                        else projectContext.remove("agent");
                                    }
                                    break;
                                }
                            }

                            if (agentDto == null && agentName != null && !agentName.isBlank()) {
                                agentDto = agentRegistryService.find(agentName).orElse(null);
                                if (agentDto != null) {
                                    log.info("Agent: Using agent '{}' resolved from registry by name '{}'", agentDto.getName(), agentName);
                                }
                            }

                            if (agentDto == null) {
                                agentDto = llmSpringService.getDefaultConfig();
                                log.warn("Agent: Agent '{}' not found in registry or recipe. Using default agent '{}'.",
                                        agentName, agentDto.getName() != null ? agentDto.getName() : agentDto.getProvider());
                            }

                            if (!requestedTools.isEmpty() && agentDto.getTools() != null && !agentDto.getTools().isEmpty()) {
                                List<String> available = agentDto.getTools();
                                List<String> filtered = requestedTools.stream()
                                        .filter(available::contains).toList();
                                if (!filtered.isEmpty()) {
                                    log.warn("Requested tools '{}' not available for agent '{}'. Available tools: {}", requestedTools, agentDto.getName(), available);
                                } else {
                                    agentDto.setTools(filtered);
                                }
                            }

                            Object originalAgent = projectContext.get("agent");
                            llmSpringService.ensureLlmThreadActive(projectContext, agentName != null ? agentName : "agent");

                            //llmSpringService.ensureLlmThreadActive(projectContext, agentName);

                            try {
                                projectContext.put("agent", agentDto);

                                if (agentDto.getBefore() instanceof String agentBefore) {
                                    newContent = autoEval(agentBefore + "\n" + newContent);
                                }

                                newContent = handleAgent(projectContext, newContent, fileNames);

                                if (agentDto.getAfter() instanceof String agentAfter) {
                                    newContent = autoEval(agentAfter + "\n" + newContent);
                                }
                            } finally {
                                if (originalAgent != null) projectContext.put("agent", originalAgent);
                                else projectContext.remove("agent");
                            }
                            break;

//                        case "agent":
//                            var agentName = evalIfSpEL(Utils.getParam(transformParameters, 0, null));
//                            if (agentName != null && ((List<AgentDto>) projectContext.get("agents")).stream().filter(it -> it.getName().equals(agentName)).findFirst().orElse(null) instanceof AgentDto agentDto) {
//                                if (agentDto.getBefore() instanceof String agentBefore) {
//                                    newContent = autoEval(agentBefore + "\n" + newContent);
//                                }
//
//                                var systemInstructions = (String) autoEval(Utils.nvl(agentDto.getSystemInstructions(), ""));
//                                if (agentDto.getTools() != null) {
//                                    var toolsDescription = toolsFunction.getTools(agentDto.getTools().toArray(new String[0]));
//
//                                    systemInstructions += """
//                                            The tools below can be used to manipulate the data. If you wanna use any of those tools,
//                                            you should answer with a JSON ARRAY, where each JSON OBJECT.
//                                            Always remember the original [REQUEST] to understand if you need to execute any other tool
//                                            before returning the final answer.
//                                            Never ask for confirmation or more info than the [REQUEST], only answer the [REQUEST] using whatever number [TOOLS] you need and how many times you need!
//                                            The tool information is NEVER incomplete. If it seems to be, you should ASK for a new tool execution following the [TEMPLATE] and [TOOLS] again and just with that, nothing else!
//
//                                            [TEMPLATE]
//                                            [
//                                                {
//                                                    "name": "fill with the tool name",
//                                                    "parameters": {
//                                                        "parameter1Name": null,               // replace null with the value expected of the parameter1Name
//                                                        "parameter2Name": null,               // replace null with the value expected of the parameter2Name
//                                                        ...,
//                                                        "parameterXName": null                // replace null with the value expected of the parameterXName
//                                                    }
//                                                }
//                                            ]
//
//                                            [TOOLS]
//                                            """;
//
//                                    systemInstructions += toolsDescription;
//                                }
//
//                                //llmService.configure(agentDto.getUuid(), agentDto.getProvider(), agentDto.getModel(), agentDto.getDeploymentName(), BigDecimal.valueOf(agentDto.getTemperature()), systemInstructions);
//
//                                var memory = "[REQUEST]\n" + newContent;
//                                newContent = handleAgent((String) newContent, agentDto.getUuid(), null);
//                                memory += "\n\n[AGENT ANSWER]\n" + newContent;
//
//                                if (agentDto.getTools() != null && !agentDto.getTools().isEmpty()) {
//                                    while (JsonUtils.isValidJson(newContent)) {
//                                        var toolsExecution = JsonUtils.readAsListOf((String) newContent, ToolDto.class);
//                                        for (var toolExecution : toolsExecution) {
//                                            log.info("Tool Execution:\n{}", JsonUtils.writeAsJsonString(toolExecution, true));
//
//                                            var toolExecutionResult = toolsFunction.invokeToolMethod(toolExecution);
//
//                                            log.info("Tool Result:\n{}", JsonUtils.writeAsJsonString(toolExecutionResult, true));
//                                            newContent = toolExecutionResult instanceof String toolExecutionString ? toolExecutionString : JsonUtils.writeAsJsonString(toolExecutionResult, true);
//                                            memory += "\n\n[TOOL EXECUTION]\n" + newContent;
//                                        }
//
//                                        newContent = handleAgent(memory, agentDto.getUuid());
//                                        memory += "\n\n[AGENT ANSWER]\n" + newContent;
//                                    }
//                                }
//
//                                if (agentDto.getAfter() instanceof String agentAfter) {
//                                    newContent = autoEval(agentAfter + "\n" + newContent);
//                                }
//                            } else {
//                                throw new RuntimeException("Agent '" + agentName + "' not found!");
//                            }
//                            break;

                        case "neo4j":
                            log.warn("The transformAction '{}' will be deprecated soon! To be replaced by @@@dbgraph", transformCommand);
                            String neo4jURL = evalIfSpEL(transformParameters.getFirst());
                            String neo4jAUTH = evalIfSpEL(transformParameters.get(1));

                            Map<String, String> headers = Map.of("Authorization", neo4jAUTH, "Content-Type", "application/json");

                            Map<String, Object> body = new ConcurrentLinkedHashMap<>();
                            var neo4jStatements = new ConcurrentLinkedList<>();

                            var neo4jJsonSource = newContent instanceof String neo4jStringBody ? (JsonUtils.isValidJson(neo4jStringBody) ? JsonUtils.readAsObject(neo4jStringBody, new ConcurrentLinkedList<>()) : null) : newContent;
                            if (neo4jJsonSource != null) {
                                List<Map<String, Object>> neo4jUpdates = GraphUtils.normalizeJsonToMapList(neo4jJsonSource);
                                neo4jStatements = GraphUtils.convertJsonToListCypherStatement(neo4jUpdates);
                            } else {
                                neo4jStatements.add(Map.of("statement", newContent));
                            }

                            body.put("statements", neo4jStatements);
                            newContent = toolsFunction.apiCall(neo4jURL, "POST", body, headers);
                            break;
                        case "graph":
                            var agensGraphStatements = new ConcurrentLinkedList<>();

                            var agensGraphJsonSource = newContent instanceof String stringBody ? (JsonUtils.isValidJson(stringBody) ? JsonUtils.readAsObject(stringBody, new ConcurrentLinkedList<>()) : null) : newContent;
                            if (agensGraphJsonSource != null) {
                                List<Map<String, Object>> agensGraphUpdates = GraphUtils.normalizeJsonToMapList(agensGraphJsonSource);
                                agensGraphStatements = GraphUtils.convertJsonToListCypherStatement(agensGraphUpdates);
                            } else {
                                agensGraphStatements.add(newContent);
                            }

                            agensGraphService.connect();
                            String labels = GraphUtils.extractLabelsByStatements(agensGraphStatements);
                            if (!labels.isBlank())
                                agensGraphService.executeCyphersNoReturn(labels);
                            newContent = agensGraphService.executeListCypher(agensGraphStatements);
                            agensGraphService.close();

                            break;
                        case "encodebase64":
                            newContent = Utils.encodeBase64(newContent);
                            break;

                        case "decodebase64":
                            newContent = Utils.decodeBase64ToAny((String) newContent);
                            break;

                        case "plantuml":
                            newContent = plantUMLService.writeToBase64((String) newContent);
                            break;

                        case "objectify":
                            Object objectifyModelExpression = Utils.getParam(transformParameters, 0, null);

                            if (objectifyModelExpression == null && newContent instanceof String newContentString) {
                                if (JsonUtils.isValidJson(newContent)) {
                                    newContent = JsonUtils.readAs(newContentString, Object.class);
                                } else if (YamlUtils.isValidYaml(newContent)) {
                                    newContent = YamlUtils.readYAML(newContentString);
                                } else if (XmlUtils.isValidXml(newContentString)) {
                                    newContent = XmlUtils.readAs(newContentString, Object.class);
                                } else {
                                    throw new IllegalStateException("The current string doesn't seems to fit any of the parser options: Json, Yaml, Xml. Content:\n" + newContent);
                                }
                            } else {
                                var nestedHistory = new ConcurrentLinkedList<TransformDto>();
                                transform.setHistory(nestedHistory);

                                Map<String, Object> objectifyModel = evalIfSpEL(objectifyModelExpression);
                                Set<Integer> visitedIds = new HashSet<>();

                                projectContext.put("self", objectifyModel);
//                            newContent = recursivelyEvaluate("", objectifyModel, projectContext, history, visitedIds, 20);
                                newContent = recursivelyEvaluate("", objectifyModel, projectContext, nestedHistory, visitedIds, 20);
                            }
                            break;

                        case "search":
                            String searchEngine = evalIfSpEL(transformParameters.getFirst());
                            String searchQuery = evalIfSpEL(Utils.getParam(transformParameters, 1, null));
                            int searchMaxResults = evalIfSpEL(Utils.getParam(transformParameters, 2, 10));

                            newContent = switch (searchEngine) {
                                case "ARXIV" -> toolsFunction.arxiv(searchQuery, searchMaxResults);
                                case "BING" -> toolsFunction.bing(searchQuery, searchMaxResults);
                                case "DUCKDUCKGO" -> toolsFunction.duckDuckGo(searchQuery, searchMaxResults);
                                default -> throw new UnsupportedOperationException("Invalid 'search' Engine option: " + searchEngine);
                            };
                            break;

                        case "api":
                            String apiURL = evalIfSpEL(transformParameters.getFirst());
                            String apiMethod = transformParameters.size() > 1 ? evalIfSpEL(transformParameters.get(1)) : "GET";
                            Object apiBody = transformParameters.size() > 2 ? evalIfSpEL(transformParameters.get(2)) : null;
                            Map<String, String> apiHeader = transformParameters.size() > 3 ? evalIfSpEL(transformParameters.get(3)) : null;
                            log.debug("API Call - URL: {}, Method: {}, Body: {}, Header: {}", apiURL, apiMethod, apiBody, apiHeader);

                            PaginationConfig paginationConfig = PaginationConfig.none();
                            if (transformParameters.size() > 4) {
                                Object paginationParam = evalIfSpEL(transformParameters.get(4));
                                paginationConfig = parsePaginationConfig(paginationParam);
                            }

                            Object apiResponse;
                            try {
                                if (paginationConfig.isNone()) {
                                    apiResponse = toolsFunction.apiCall(apiURL, apiMethod, apiBody, apiHeader);
                                } else {
                                    apiResponse = executeApiWithPagination(apiURL, apiMethod, apiBody, apiHeader, paginationConfig);
                                }
                            } catch (Exception ex) {
                                log.error("API call failed: {}", ex.getMessage(), ex);
                                apiResponse = null;
                            }

                            newContent = apiResponse;
                            break;

                        default:
                            String customTransformKey = CUSTOM_TRANSFORMS.keySet().stream().filter(it -> it.equalsIgnoreCase(transformCommand)).findFirst().orElse(null);
                            if (customTransformKey != null && CUSTOM_TRANSFORMS.get(customTransformKey) instanceof ITransform groovyCustomTransform) {
                                newContent = groovyCustomTransform.execute(applicationContext, projectContext, (String) newContent, transform.getOriginalTransformParams());
                            } else {
                                throw new RuntimeException("Transform command not recognized: " + transformCommand);
                            }
                    }
                } catch (Exception ex) {
                    Throwable cause = ex;
                    while (cause != null) {
                        if (InterruptedException.class.isAssignableFrom(cause.getClass())) {
                            throw ex;
                        }

                        cause = cause.getCause();
                    }

                    long end = System.currentTimeMillis();
                    transform.setMillisSpent(end - start);

                    if (attempts-- > 0) {
                        transformIndex = retryCheckpointIndex;

                        log.warn("Something went wrong trying to execute a '{}', but there's a retry checkpoint to be followed before dismiss! Input parameters:\n{}", transformCommand, transformParameters);
                        if (isValidSpEL(retryCheckpointExpression)) {
                            try {
                                newContent = evalSpEL(retryCheckpointExpression);
                            } catch (Exception ex2) {
                                log.error("Failed to evaluate checkpointRetryExpression: {}\nConsidering the original checkpointContent as reference!", retryCheckpointExpression);
                            }
                        } else {
                            newContent = retryCheckpointContent;
                        }

                        checkpointRecovered = true;
                    } else {
                        history.add(
                                TransformDto.builder()
                                        .name("Exception")
                                        .content(ex.getMessage() + "\n\n\n" + Utils.getStackTraceAsString(ex))
                                        .build()
                        );

                        if (defaultExpression != null) {
                            log.warn("Using the last 'default' configured to set the value!");
                            newContent = autoEval(defaultExpression);
                            transformIndex = transforms.size();
                            var defaultFinalTransformDto = TransformDto.builder().name("default").content(newContent).build();
                            history.add(defaultFinalTransformDto);
                        } else {
                            throw ex;
                        }
                    }
                }
            }

            if (isCacheable) {
                log.info("Cache updated");
                tempCache.put(transformCacheHash, newContent);
            }

            if (!checkpointRecovered && !transform.isUpdate()) {
                newContent = backupContent;
            }

            projectContext.put("content", newContent);

            long end = System.currentTimeMillis();
            transform.setMillisSpent(end - start);
            transform.getCaches().addAll(tempCache.keySet());
        }

        if (!tempCache.isEmpty()) {
            log.info("Cache confirmed!");
            flowCache.putAll(tempCache);
        }

        if (!cacheDataList.isEmpty()) {
            var cacheMap = Utils.nvl((Map<String, Object>) Utils.anyCollectionGet(projectContext, "cacheMap"), new ConcurrentLinkedHashMap<String, Object>());

            var cacheHashMap = new ConcurrentLinkedHashMap<String, Object>();
            cacheMap.put(cachePath, cacheHashMap);
            cacheHashMap.put("hash", cacheHash);
            cacheHashMap.put("content", newContent);
            var cacheHashMapVars = new ConcurrentLinkedHashMap<String, Object>();
            cacheHashMap.put("vars", cacheHashMapVars);

            cacheDataList.forEach(it -> {
                cacheHashMapVars.put((String) it, Utils.anyCollectionGet(projectContext, (String) it));
            });
        }

        return newContent;
    }

    private static final String CONSTANT_USE_LLM_THREAD = "UseLLMThread";
    private static final String CONSTANT_LLM_THREAD_KEY = "LLMThreadKey";

    private PaginationConfig parsePaginationConfig(Object param) {
        if (param == null) {
            return PaginationConfig.none();
        }

        Map<String, Object> configMap;
        if (param instanceof Map) {
            configMap = (Map<String, Object>) param;
        } else if (param instanceof String) {
            String paramStr = ((String) param).trim();
            if (JsonUtils.isValidJson(paramStr)) {
                configMap = JsonUtils.readAsMap(paramStr, Object.class);
            } else {
                return PaginationConfig.none();
            }
        } else {
            return PaginationConfig.none();
        }
        return PaginationConfig.fromMap(configMap);
    }

    private Object executeApiWithPagination(
            String baseUrl,
            String method,
            Object initialBody,
            Map<String, String> headers,
            PaginationConfig config
    ) throws Exception{
        List<Map<String, Object>> allItems = new ConcurrentLinkedList<>();
        int pageCount = 0;
        int totalItems = 0;
        Object paginationState = config.getInitialState();

        log.info("Starting paginated API call: type={}, maxPages={}, maxItems={}", config.getType(), config.getMaxPages(), config.getMaxItems());

        while (pageCount < config.getMaxPages()) {
            pageCount++;

            PaginationConfig.PaginationRequest request = config.buildRequest(paginationState, initialBody);
            String url = request.hasQueryParams() ? baseUrl + "?" + request.getQueryString() : baseUrl;
            Object body = request.getBody();

            log.debug("Fetching page {}: url={}, body={}", pageCount, url, body);

            if (config.getRateLimitDelayMs() > 0 && pageCount > 1) {
                log.info("Waiting {} ms to respect rate limit...", config.getRateLimitDelayMs());
                Thread.sleep(config.getRateLimitDelayMs());
            }

            Object responseObj = toolsFunction.apiCall(url, method, body, headers);

            Map<String, Object> response;
            if ((responseObj instanceof String)) {
                response = JsonUtils.readAsMap((String) responseObj);
            } else if (responseObj instanceof Map) {
                response = (Map<String, Object>) responseObj;
            } else {
                throw new IllegalStateException("Unsupported response type for pagination: " + responseObj.getClass());
            }

            List<Map<String, Object>> pageItems = config.extractItems(response);
            allItems.addAll(pageItems);
            totalItems += pageItems.size();

            log.debug("Page {} returned {} items, total so far: {}", pageCount, pageItems.size(), totalItems);

            if (config.isLastPage(response, paginationState)) {
                log.info("Reached last page at page {}.", pageCount);
                break;
            }

            paginationState = config.updateState(response, paginationState);

            if (config.getMaxItems() > 0 && totalItems >= config.getMaxItems()) {
                log.info("Reached max items limit ({}) at page {}.", config.getMaxItems(), pageCount);
                break;
            }
        }

        log.info("Pagination complete: {} pages, {} total items.", pageCount, totalItems);

        return Map.of(
                "items", allItems,
                "totalPages", pageCount,
                "totalItems", totalItems
        );
    }

    public String handleAgent(Map<String, Object> projectContext, Object content, List<String> fileNames) throws Exception {
        return handleAgent(projectContext, content, fileNames, 3);
    }

    public String handleAgent(Map<String, Object> projectContext, Object content, List<String> fileNames, int retries) throws Exception {
        Object config = projectContext.get("agent");
        if (config == null) {
            log.warn("No agent config found, using default prompt!");
            config = llmSpringService.getDefaultConfig();
        } else if (config instanceof String alias && !alias.isBlank()) {
            config = llmSpringService.parseAgentConfig(alias);
            projectContext.put("agent", config);
        }
        Map<String, Object> files = llmSpringService.getFilesFromContext(projectContext);
        log.debug("fileNames: {}, files: {}", fileNames, files);
        if ((files == null || files.isEmpty()) && (fileNames != null && !fileNames.isEmpty())) {
            throw new IllegalStateException("No files found in context, but file names were provided in agent config!");
        }

        final Object finalConfig = config;

        //llmSpringService.ensureLlmThreadActive(projectContext, "agent");
        String threadId = (String) projectContext.get(CONSTANT_LLM_THREAD_KEY);

        do {
            try {
                if (finalConfig instanceof AgentEmbConfigDto embeddingConfig) {
                    String result = Arrays.deepToString(llmEmbeddingSpringService.promptEmbeddingAsArray(content.toString(), embeddingConfig));
                    log.debug("LLM Response: \n{}", result);
                    return result;
                } else if (finalConfig instanceof AgentDto agentConfig) {
                    if (fileNames == null || fileNames.isEmpty() || files == null || files.isEmpty()) {
                        String result = llmSpringService.prompt(content.toString(), agentConfig, threadId);
                        log.debug("LLM Response: {}", result);
                        return result;
                    }

                    boolean urlOnly = llmSpringService.isUrlOlyMode(projectContext, agentConfig);
                    if (urlOnly || fileNames == null || fileNames.isEmpty() || files == null || files.isEmpty()) {
                        String result = llmSpringService.prompt(content.toString(), agentConfig, threadId);
                        log.debug("LLM Response: {}", result);
                        return result;
                    }

                    List<MultipartFile> multipartFiles = llmSpringService.prepareMultipartFiles(files, fileNames);
                    String result = llmSpringService.promptWithFile(content.toString(), multipartFiles, agentConfig, threadId);
                    log.debug("LLM Response: \n{}", result);
                    return result;
                }
            } catch (Exception e) {
                Throwable cause = e;
                while (cause != null) {
                    if (InterruptedException.class.isAssignableFrom(cause.getClass())) {
                        log.error("A forced interruption of the execution was requested! Message: {}", cause.getMessage());
                        throw e;
                    }

                    cause = cause.getCause();
                }

                log.error("Error during LLM execution. Retries left: {}. Error message: {}", retries, e.getMessage());
                if (--retries <= 0) {
                    if (e instanceof FileNotFoundException || e instanceof IllegalStateException) {
                        throw e;
                    }
                    throw new RuntimeException("Error during LLM execution. Retries left: " + retries + ". Error message: " + e.getMessage());
                }

                log.info("Waiting 15 seconds before trying again!");
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException ie) {
                    log.error("Sleep interrupted — forced stop requested!");
                    throw ie;
                }
            }
        } while (retries > 0);

        return null;
    }

    @Override
    public Object autoEval(String content) throws Exception {
        return autoEval(content, new ConcurrentLinkedList<>());
    }

    @Override
    public Object autoEval(String content, List<TransformDto> history) throws Exception {
        if (content == null) {
            return null;
        }

        if (history == null) {
            history = new ConcurrentLinkedList<>();
        }

        Object result = content;
        if (isValidSpEL(content)) {
            result = autoEvalExpression(content, history);
        }

        // TODO: missing history control. BUT, we need to start creating nested histories, like the history inside objectify model is just a child of objectify history itself, so the UX tools can choose to get deeper details or not
        if (result instanceof String resultString && isValidJsonPath(resultString)) {
            result = Utils.anyCollectionGet(contextService.getProjectContext(), resultString);
        }

        if (result instanceof String resultString) {
            return autoEvalStringTransforms(resultString, history);
        }

        return result;
    }

    public String includeMissingVariableDeclarations(Map<String, Object> projectContext, String content, String templateFreemarkerPath, String currentClassPlaceholder, String undeclaredVariablesPlaceholder) throws Exception {
        List<Object> transformParams = new LinkedList<>();
        transformParams.add(templateFreemarkerPath);
        transformParams.add(currentClassPlaceholder);
        transformParams.add(undeclaredVariablesPlaceholder);
        return includeMissingVariableDeclarations(projectContext, content, transformParams);
    }

    public String includeMissingVariableDeclarations(Map<String, Object> projectContext, String content, List<Object> parsedTransformParams) throws Exception {
        String transformContentPlaceholder = (String) parsedTransformParams.getFirst();
        String undeclaredVariablesPlaceholder = (String) parsedTransformParams.get(1);
        String template = (String) parsedTransformParams.get(2);

        String javaCode = (String) Utils.extractMarkdownCode(content);
        Set<String> undeclaredVariables = JavaUtils.getAllUndeclaredVariables(javaCode);
        if (undeclaredVariables.isEmpty()) {
            return content;
        }

        log.info("includeMissingVariableDeclarations: will try to solve {} missing variables.", undeclaredVariables.size());

        String solvedPromptTemplate = (String) autoEval(template);

        solvedPromptTemplate = solvedPromptTemplate.replace(transformContentPlaceholder, content);
        solvedPromptTemplate = solvedPromptTemplate.replace(undeclaredVariablesPlaceholder, String.join("\n", undeclaredVariables));

        String llmAnswer = handleAgent(projectContext, solvedPromptTemplate, null);
        String extractMarkdownCodeFromLlmAnswer = (String) Utils.extractMarkdownCode(llmAnswer);

        Map<String, FieldDeclaration> newFieldVariableNames = JavaUtils.getAllFieldVariablesMap(extractMarkdownCodeFromLlmAnswer);

        var setOfFieldsToIncludeThatMatchUndeclaredSet = newFieldVariableNames.entrySet().stream().filter(it -> undeclaredVariables.contains(it.getKey())).map(Map.Entry::getValue).collect(Collectors.toSet());
        String newCode = JavaUtils.addAllFieldsToClass(javaCode, setOfFieldsToIncludeThatMatchUndeclaredSet);

        return includeMissingVariableDeclarations(projectContext, newCode, parsedTransformParams);
    }

    public void addHistory(List<TransformDto> fileHistory, String name, String content, Long millisSpent) {
        var transformDto = TransformDto.builder()
                .name(name)
                .content(content)
                .millisSpent(millisSpent)
                .build();

        fileHistory.add(transformDto);
    }

    private void copySheetContent(Sheet sourceSheet, Sheet targetSheet, Workbook targetWorkbook) {
        for (int i = sourceSheet.getFirstRowNum(); i <= sourceSheet.getLastRowNum(); i++) {
            Row sourceRow = sourceSheet.getRow(i);
            if (sourceRow == null) continue;

            Row targetRow = targetSheet.createRow(i);
            copyRow(sourceRow, targetRow, targetWorkbook);
        }

        // Copy merged regions
        for (int i = 0; i < sourceSheet.getNumMergedRegions(); i++) {
            var newRegion = sourceSheet.getMergedRegion(i).copy();
            if (!isRegionAlreadyMerged(targetSheet, newRegion)) {
                targetSheet.addMergedRegion(newRegion);
            }
        }
    }

    private boolean isRegionAlreadyMerged(Sheet sheet, CellRangeAddress newRegion) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).formatAsString().equals(newRegion.formatAsString())) {
                return true;
            }
        }
        return false;
    }

    private void copyRow(Row sourceRow, Row targetRow, Workbook workbook) {
        for (int j = sourceRow.getFirstCellNum(); j < sourceRow.getLastCellNum(); j++) {
            Cell sourceCell = sourceRow.getCell(j);
            if (sourceCell == null) continue;

            Cell targetCell = targetRow.createCell(j);
            copyCellValue(sourceCell, targetCell, workbook);
        }
    }

    private void copyCellValue(Cell sourceCell, Cell targetCell, Workbook workbook) {
        switch (sourceCell.getCellType()) {
            case STRING -> targetCell.setCellValue(sourceCell.getStringCellValue());
            case NUMERIC -> targetCell.setCellValue(sourceCell.getNumericCellValue());
            case BOOLEAN -> targetCell.setCellValue(sourceCell.getBooleanCellValue());
            case FORMULA -> targetCell.setCellFormula(sourceCell.getCellFormula());
            case ERROR -> targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
            case BLANK -> targetCell.setBlank();
            default -> targetCell.setCellValue(sourceCell.toString());
        }

        CellStyle newStyle = workbook.createCellStyle();
        newStyle.cloneStyleFrom(sourceCell.getCellStyle());
        targetCell.setCellStyle(newStyle);
    }

    public Object recursivelyEvaluate(
            String currentPath,
            Object input,
            Map<String, Object> context,
            List<TransformDto> history,
            Set<Integer> visitedIds,
            int depth
    ) throws Exception {
        if (depth > 100) return "[MAX_DEPTH_EXCEEDED]";

        try {
            if (input instanceof String str) {
                Object evaluated = autoEval(str, history);
                if (evaluated != null && !evaluated.equals(str)) {
                    return recursivelyEvaluate(currentPath, evaluated, context, history, visitedIds, depth + 1);
                }
                return evaluated;
            } else if (input instanceof Map<?, ?> map) {
                int id = System.identityHashCode(input);
                if (!visitedIds.add(id)) return "[CIRCULAR_REFERENCE]";

                try {
                    Map<String, Object> result = new ConcurrentLinkedHashMap<>();
                    var parent = context.get("self");
                    var parentRelationship = (String) Utils.anyCollectionGet(parent, "parentRelationship", "CONTAINS");

                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        var keyExpr = String.valueOf(entry.getKey());
                        List<String> params = (List<String>) Utils.convertToConcurrent(Arrays.stream(keyExpr.split("\\|(?![^$]*})")).toList());
                        var parentRelationshipAndKey = params.removeFirst().split(":");
                        var key = (parentRelationshipAndKey.length > 1 ? parentRelationshipAndKey[1] : parentRelationshipAndKey[0]).trim();
                        var nextRelationship = (parentRelationshipAndKey.length > 1 ? parentRelationshipAndKey[0] : parentRelationship).trim();

                        var newItems = new ConcurrentLinkedList<>();

                        do {
                            context.put("parent", parent);
                            context.put("self", result);
                            context.put("key", key);
                            result.put("parent", parent);
                            result.put("parentRelationship", nextRelationship);

                            var items = params.isEmpty() ? null : params.removeFirst();
                            if (items != null) {
                                Utils.anyCollectionSet(result, key, newItems);

                                var itemsList = autoEval(items, history);
                                if (itemsList == null) {
                                    continue;
                                }

                                if (!(itemsList instanceof List<?>)) {
                                    itemsList = new ConcurrentLinkedList<>(List.of(itemsList));
                                }

                                List<?> list = (List<?>) itemsList;
                                int i = 0;
                                for (var item : list) {
                                    result.put("parent", parent);
                                    result.put("parentRelationship", nextRelationship);
                                    Object childEvaluated = ContextUtils.withTemporaryContext(
                                            context,
                                            Map.of(
                                                    "parent", parent,
                                                    "self", result,
                                                    "key", key,
                                                    "item", item,
                                                    "index", i++
                                            ),
                                            () -> recursivelyEvaluate(
                                                    currentPath + "." + keyExpr,
                                                    entry.getValue(),
                                                    context,
                                                    history,
                                                    visitedIds,
                                                    depth + 1
                                            )
                                    );

                                    newItems.add(childEvaluated);
                                }
                            } else {
                                Object childEvaluated = recursivelyEvaluate(currentPath + "." + keyExpr, entry.getValue(), context, history, visitedIds, depth + 1);
                                Utils.anyCollectionSet(result, key, childEvaluated);
                            }
                        } while (!params.isEmpty());
                    }
                    return result;
                } catch (Exception ex) {
                    throw ex;
                } finally {
                    visitedIds.remove(id);
                }
            } else if (input instanceof List<?> list) {
                int id = System.identityHashCode(input);
                if (!visitedIds.add(id)) return "[CIRCULAR_REFERENCE]";

                try {
                    List<Object> result = new ConcurrentLinkedList<>();
                    var parent = context.get("self");

                    for (int i = 0; i < list.size(); i++) {
                        int finalI = i;
                        Object childEvaluated = ContextUtils.withTemporaryContext(
                                context,
                                Map.of(
                                        "parent", parent,
                                        "self", result,
                                        "index", i
                                ),
                                () -> recursivelyEvaluate(
                                        currentPath + "[" + finalI + "]",
                                        list.get(finalI),
                                        new ConcurrentLinkedHashMap<>(context),
                                        history,
                                        visitedIds,
                                        depth + 1
                                )
                        );

                        result.add(childEvaluated);
                    }
                    return result;
                } finally {
                    visitedIds.remove(id);
                }
            }
        } catch (Exception e) {
            log.error("Objectify error evaluating path '{}': {}", currentPath, e.getMessage(), e);
            throw e;
        }

        return input;
    }

    public Map<String, Object> python(String code, Map<String, Object> vars) throws IOException, InterruptedException, TimeoutException, PythonException {
        if (this.worker == null) {
            this.worker = new PythonWorker("python", null);
        }

        var pythonReturnAndVars = worker.exec(code, vars);
        if (pythonReturnAndVars.get("error") instanceof Map<?, ?> em) {
            Map<String, Object> emTyped = (Map<String, Object>) em;

            String type  = String.valueOf(emTyped.getOrDefault("type", "Exception"));
            String msg   = String.valueOf(emTyped.getOrDefault("message", ""));
            String trace = String.valueOf(emTyped.getOrDefault("trace", ""));

            @SuppressWarnings("unchecked")
            Map<String, Object> pyVars = (Map<String, Object>) pythonReturnAndVars.getOrDefault("vars", Map.of());
            Object pyReturn = pythonReturnAndVars.get("return");

            throw new PythonException(type, msg, trace, pyReturn, pyVars);
        }

        return pythonReturnAndVars;
    }
}
