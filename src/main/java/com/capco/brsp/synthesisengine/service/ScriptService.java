package com.capco.brsp.synthesisengine.service;

import ch.qos.logback.core.util.StringUtil;
import com.capco.brsp.synthesisengine.dto.*;
import com.capco.brsp.synthesisengine.dto.grammars.Grammar;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.stream.IntStreams;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.capco.brsp.synthesisengine.service.LLMSpringService.CONSTANT_LLM_THREAD_KEY;
import static com.capco.brsp.synthesisengine.service.LLMSpringService.CONSTANT_USE_LLM_THREAD;

@Slf4j
@Component
@RequiredArgsConstructor
@Setter
@Service(value = "scriptService")
public class ScriptService implements IScriptService {
    private static final Map<String, Map<String, Object>> TRANSFORM_CACHE = new ConcurrentLinkedHashMap<>();
    private static final Map<String, ITransform> CUSTOM_TRANSFORMS = new ConcurrentLinkedHashMap<>();
    private static final String SPEL_EXPRESSION_DELIMITER = "^\\s*\\$\\{([\\s\\S]+)}\\s*$";
    private static final Pattern PATTERN_SPEL_EXPRESSION = Pattern.compile(SPEL_EXPRESSION_DELIMITER);
    private final ApplicationContext applicationContext;
    @Autowired
    @Qualifier("plantUMLService")
    private PlantUMLService plantUMLService;
    @Autowired
    @Qualifier("llmSpringService")
    private LLMSpringService llmSpringService;
    @Autowired
    @Qualifier(value = "agentRegistryService")
    private AgentRegistryService agentRegistryService;
    @Autowired
    @Qualifier("llmEmbeddingSpringService")
    private LLMEmbeddingSpringService llmEmbeddingSpringService;
    @Autowired
    @Qualifier("contextService")
    private ContextService contextService;
    @Autowired
    private ToolsFunction toolsFunction;

    @Qualifier("beanResolver")
    private final BeanResolver beanResolver;
    private final Configuration freemakerConfig = new Configuration(Configuration.getVersion());
    private final StringTemplateLoader freemarkerTemplateLoader = new StringTemplateLoader();
    private final ExpressionParser parser = new SpelExpressionParser();
    Map<String, IExecutor> executorsCache = new NullableConcurrentHashMap<>();

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

    public void removeCache(String flowKey, String cacheHash) {
        TRANSFORM_CACHE.get(flowKey).remove(cacheHash);
    }

    public String evalSpELItems(String text) {
        var context = getSpELContext(contextService.getProjectContext());

        Pattern pattern = Pattern.compile("\\$\\{(.*?)}\\$");
        Matcher matcher = pattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object value = parser.parseExpression(expression).getValue(context);
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

    public <T> T evalIfSpEL(Object value) {
        if (value instanceof String str && isValidSpEL(str)) {
            return (T) evalSpEL(str);
        }
        return (T) value;
    }

    public Object evalSpEL(String expression) {
        String expressionContent = getSpELContent(expression);

        var context = getSpELContext(contextService.getProjectContext());

        try {
            return parser.parseExpression(expressionContent).getValue(context);
        } catch (SpelEvaluationException ex) {
            throw new SpelEvaluationExceptionDetails(expression, ex);
        } catch (Exception ex) {
            throw ex;
        }
    }

    public Object evalSpEL(Map<String, Object> context, String expression) {
        String expressionContent = getSpELContent(expression);

        var standardEvaluationContext = getSpELContext(context);

        return parser.parseExpression(expressionContent).getValue(standardEvaluationContext);
    }

    public String getSpELContent(String expression) {
        if (!isValidSpEL(expression)) {
            throw new IllegalStateException("Not a valid single expression! Expression: " + expression);
        }

        var matcher = PATTERN_SPEL_EXPRESSION.matcher(expression);
        return matcher.results().findFirst().map(it -> it.group(1)).orElse(null);
    }

    public boolean isValidSpEL(String expression) {
        if (StringUtil.isNullOrEmpty(expression)) {
            return false;
        }

        return expression.matches(SPEL_EXPRESSION_DELIMITER);
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
        binding.setVariable("contextService", contextService);
        binding.setVariable("projectContext", contextService.getProjectContext());
        binding.setVariable("YamlUtils", YamlUtils.class);
        binding.setVariable("scriptService", this);
        binding.setVariable("jsonUtils", JsonUtils.class);

        GroovyShell shell = new GroovyShell(binding);
        return shell.evaluate(groovyScript);
    }

    // Brakepoint to debug groovy scripts
    public Object evalGroovy(String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        IExecutor groovyObject = getGroovyExecutor(scriptContentOrFilePath);

        return groovyObject.execute(applicationContext, contextService.getProjectContext());
    }

    public IExecutor getGroovyExecutor(String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final Class<IExecutor> groovyClass;

        String executorHashSignature = Utils.hashString(scriptContentOrFilePath);
        if (executorsCache.containsKey(executorHashSignature)) {
            return executorsCache.get(executorHashSignature);
        }

        // Use the current thread's classloader as the parent
//        Thread.currentThread().setContextClassLoader();
//        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        GroovyClassLoader classLoader = new GroovyClassLoader(applicationContext.getClassLoader()); // Do NOT close this

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

    public Object autoEvalStringTransforms(String content) throws Exception {
        return autoEvalStringTransforms(content, new ConcurrentLinkedList<>());
    }

    public Object autoEvalStringTransforms(String content, List<TransformDto> history) throws Exception {
        boolean foundFirstNonAtLine = false;
        List<String> transformLines = new ConcurrentLinkedList<>();
        List<String> remainingContent = new ConcurrentLinkedList<>();
        List<String> lines = content.lines().toList();
        for (String line : lines) {
            if (!foundFirstNonAtLine) {
                if (line.stripLeading().startsWith("@@@")) {
                    transformLines.add(line);
                } else if (!line.isBlank()) {
                    foundFirstNonAtLine = true;
                    remainingContent.add(line);
                }
            } else {
                remainingContent.add(line);
            }
        }

        var flowKey = contextService.getFlowKey();
        var projectContext = contextService.getProjectContext();

        String newContent = String.join("\n", remainingContent);
        if (transformLines.isEmpty()) {
            var transformDto = TransformDto.builder().name("Static Content").content(newContent).build();
            history.add(transformDto);
        }
        projectContext.put("content", newContent);

        String defaultExpression = null;
        String retryCheckpointContent = "";
        String retryCheckpointExpression = "";
        int retryCheckpointIndex = -1;

        String cacheHash = null;
        String cachePath = null;
        List<Object> cacheDataList = new ConcurrentLinkedList<>();

        int attempts = -1;
        int transformIndex = 0;

        Map<String, CheckpointDto> checkpointMap = new ConcurrentLinkedHashMap<>();
        Map<String, Object> tempCache = new ConcurrentLinkedHashMap<>();
        if (TRANSFORM_CACHE.get(flowKey) == null) {
            TRANSFORM_CACHE.put(flowKey, new ConcurrentLinkedHashMap<>());
        }
        var flowCache = TRANSFORM_CACHE.get(flowKey);

        LB_WHILE:
        while (transformIndex < transformLines.size()) {
            long start = System.currentTimeMillis();

            String transformCommand = "";

            var transformActionLine = transformLines.get(transformIndex++);

            var transformFull = Objects.requireNonNull(transformActionLine).trim().substring(3);
            var transformSplitted = new ConcurrentLinkedList<>(Arrays.stream(transformFull.split("@set")).toList());
            var transform = transformSplitted.removeFirst();
            String transformTrimmed = transform.trim();

            var transformParams = Utils.getRegexGroup(transformTrimmed, "^\\w+\\((.*)\\)$", 1);

            transformCommand = Utils.getTextBefore(transformTrimmed, "\\(");

            String historyContent = String.join("\n", transformLines.subList(transformIndex - 1, transformLines.size())) + "\n" + newContent;
            var transformDto = TransformDto.builder().name(transformCommand).content(historyContent).build();
            history.add(transformDto);

            boolean dontUpdateContent = false;
            String backupContent = newContent;
            if (transformCommand.startsWith("_")) {
                transformCommand = transformCommand.substring(1);
                dontUpdateContent = true;
            }

            var parsedTransformParams = Utils.splitParams(transformParams);
            var transformDefaultParams = Utils.anyCollectionGet(projectContext, "recipe.config.transformDefaultParams." + transformCommand);
            if (transformDefaultParams instanceof List<?> transformDefaultParamsList) {
                parsedTransformParams = Utils.nonNullOfAnyList(parsedTransformParams, transformDefaultParamsList);
            }

            projectContext.entrySet().removeIf(it -> it.getKey().startsWith("_arg"));
            List<Object> finalParsedTransformParams = parsedTransformParams;
            IntStreams.range(parsedTransformParams.size()).forEach(parsedTransformParamIndex -> projectContext.put("_arg" + parsedTransformParamIndex, finalParsedTransformParams.get(parsedTransformParamIndex)));

            var isCacheable = projectContext.get("recipe") instanceof Map<?, ?> recipeMap
                    && recipeMap.get("caches") instanceof Map<?, ?> cachesMap
                    && cachesMap.get("transforms") instanceof List<?> cachesTransformList
                    && cachesTransformList.contains(transformCommand.toLowerCase());
            var transformCacheHash = Utils.hashString(transformParams + newContent);

            if (isCacheable && flowCache.get(transformCacheHash) instanceof String cachedContent) {
                log.info("Restored by cache!");
                newContent = cachedContent;
            } else {
                try {
                    switch (transformCommand.toLowerCase()) {
                        case "replacer":
                            String fileBase64 = evalIfSpEL(Utils.getParam(parsedTransformParams, 0, null));
                            byte[] fileBytes = Utils.decodeBase64(fileBase64);
                            List<String> sheets = evalIfSpEL(Utils.getParam(parsedTransformParams, 1, new ConcurrentLinkedList<>(List.of("MySheet"))));

                            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes)) {
                                try (Workbook templateWorkbook = new XSSFWorkbook(byteArrayInputStream); Workbook newWorkbook = new XSSFWorkbook()) {
                                    Sheet sourceSheet = templateWorkbook.getSheetAt(0);

                                    for (var sheetName : sheets) {
                                        try {
                                            projectContext.put("sheetName", sheetName);
                                            Sheet sheet = newWorkbook.createSheet(sheetName);
                                            copySheetContent(sourceSheet, sheet, newWorkbook);

                                            for (Row row : sheet) {
                                                for (Cell cell : row) {
                                                    if (cell.getCellType() == CellType.STRING) {
                                                        String original = cell.getStringCellValue();

                                                        List<String> originalPlaceholders = Utils.getAllRegexGroup(original, "(\\$\\{[\\s\\S]+?})", 1);
                                                        for (String originalPlaceholder : originalPlaceholders) {
                                                            String updated = Utils.nvl(evalIfSpEL(originalPlaceholder), "");
                                                            cell.setCellValue(updated);
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            projectContext.remove("sheetName");
                                        }
                                    }

                                    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream()) {
                                        newWorkbook.write(outputByteArrayStream);

                                        var bytes = outputByteArrayStream.toByteArray();

                                        String mimeType = FileUtils.getMimeType(bytes);

                                        String base64String = Base64.getEncoder().encodeToString(bytes);

                                        newContent = "data:" + mimeType + ";base64," + base64String;
                                    }

                                    try (FileOutputStream newFile = new FileOutputStream("C:\\Users\\MLMU\\OneDrive - Capco\\Documents\\Backup\\Procergs\\Procergs - Assessments - EN-US - 2025-07-04 - v2.xlsx")) {
                                        newWorkbook.write(newFile);
                                    }
                                }
                            }
                            break;

                        case "objectify":
                            Object objectifiedObject;

                            String objectifyModelExpression = Utils.getParam(parsedTransformParams, 0, null);
                            if (objectifyModelExpression == null) {
                                if (JsonUtils.isValidJson(newContent)) {
                                    objectifiedObject = JsonUtils.readAsObject(newContent, null);
                                } else if (YamlUtils.isValidYaml(newContent)) {
                                    objectifiedObject = YamlUtils.readYAML(newContent);
                                } else if (XmlUtils.isValidXml(newContent)) {
                                    objectifiedObject = XmlUtils.readAs(newContent, Object.class);
                                } else {
                                    throw new IllegalStateException("The current string doesn't seems to fit any of the parser options: Json, Yaml, Xml. Content:\n" + newContent);
                                }
                            } else {
                                Map<String, Object> objectifyModel = evalIfSpEL(objectifyModelExpression);
//                            var traversedMap = new ConcurrentLinkedHashMap<String, Object>();
//                            Utils.traverseJsonPath(traversedMap, objectifyModel, "", key -> key);

                                Map<String, Object> solvedMap = new LinkedHashMap<>();
                                Set<Integer> visitedIds = new HashSet<>();

                                for (var entry : objectifyModel.entrySet()) {
                                    var keyExpr = entry.getKey();
                                    List<String> keys = (List<String>) Utils.convertToConcurrent(Arrays.stream(keyExpr.split("\\|(?![^$]*})")).toList());
                                    var key = keys.getFirst();

                                    projectContext.put("parent", objectifyModel);
                                    projectContext.put("self", solvedMap);
                                    projectContext.put("key", key);
                                    solvedMap.put("parent", objectifyModel);

                                    Object evaluated = recursivelyEvaluate("", entry.getValue(), projectContext, history, visitedIds, 20);
                                    Utils.anyCollectionSet(solvedMap, key, evaluated);
                                }

                                objectifiedObject = solvedMap;
                            }

                            try {
                                projectContext.put("objectified", objectifiedObject);

                                Object selected = objectifiedObject;
                                String objectifiedSelection = Utils.getParam(parsedTransformParams, 2, null);
                                if (isValidSpEL(objectifiedSelection)) {
                                    selected = evalIfSpEL(objectifiedSelection);
                                }

                                projectContext.put("selected", selected);

                                newContent = JsonUtils.writeAsJsonString(selected, true);

                                var objectifyTarget = Utils.getParam(parsedTransformParams, 1, null);
                                objectifyTarget = evalIfSpEL(objectifyTarget);
                                if (objectifyTarget instanceof String objectifyTargetString) {
                                    Utils.anyCollectionSet(projectContext, objectifyTargetString, selected);
                                }
                            } finally {
                                projectContext.remove("this");
                            }
                            break;

                        case "gitsync":
                            Map<String, String> gitlabHeader = new ConcurrentLinkedHashMap<>();
                            gitlabHeader.put("PRIVATE-TOKEN", "");
                            gitlabHeader.put("Content-Type", "application/json");

                            String rootFolder = FileUtils.absolutePathJoin(FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH, flowKey).toString();
                            var gitSyncParams = (Map<String, String>) evalIfSpEL(parsedTransformParams.getFirst());
                            gitSyncParams.forEach((gitSyncTargetFolder, gitSyncRepositoryPath) -> {
                                Map<String, Object> body = Map.of(
                                        "name", gitSyncTargetFolder,
                                        "description", gitSyncTargetFolder,
                                        "path", gitSyncTargetFolder
                                );

                                List<Map<String, Object>> obj = (List<Map<String, Object>>) toolsFunction.apiCall("https://gitlab.com/api/v4/projects?membership=true", "GET", null, gitlabHeader);
                                String finalGitSyncTargetFolder = gitSyncTargetFolder;
                                boolean contains = obj.stream().anyMatch(it -> finalGitSyncTargetFolder.equals(it.get("path")));
                                if (!contains) {
                                    toolsFunction.apiCall("https://gitlab.com/api/v4/projects/", "POST", body, gitlabHeader);
                                }

                                if (!FileUtils.isAbsolutePath(gitSyncTargetFolder)) {
                                    gitSyncTargetFolder = FileUtils.absolutePathJoin(rootFolder, gitSyncTargetFolder).toString();
                                }

                                File microserviceFolderFile = new File(gitSyncTargetFolder);
                                if (!microserviceFolderFile.exists()) {
                                    try {
                                        toolsFunction.shellRun(null, "git clone " + gitSyncRepositoryPath);
                                    } catch (InterruptedException | IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                try {
                                    toolsFunction.shellRun(null, "git -C " + gitSyncTargetFolder + " pull");
                                } catch (InterruptedException | IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            break;

                        case "gitcommit":
                            try {
                                String gitCommitSubFolderParam = Utils.castOrDefault(evalSpELOrReturn((String) parsedTransformParams.getFirst()), String.class, "");
                                if (!gitCommitSubFolderParam.isBlank()) {
                                    gitCommitSubFolderParam = " -C " + gitCommitSubFolderParam;
                                }

                                toolsFunction.shellRun(null, "git " + gitCommitSubFolderParam + " add .");
                                toolsFunction.shellRun(null, "git " + gitCommitSubFolderParam + " commit -m \"" + newContent + "\"");
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            break;

                        case "gitpush":
                            try {
                                String gitCommitSubFolderParam = (String) parsedTransformParams.getFirst();
                                gitCommitSubFolderParam = Utils.castOrDefault(evalSpELOrReturn(gitCommitSubFolderParam), String.class, "");
                                if (!gitCommitSubFolderParam.isBlank()) {
                                    gitCommitSubFolderParam = " -C " + gitCommitSubFolderParam;
                                }

                                String gitRepositoryPath = (String) parsedTransformParams.get(2);
                                gitRepositoryPath = Utils.castOrDefault(evalSpELOrReturn(gitRepositoryPath), String.class, "");

                                toolsFunction.shellRun(null, "git " + gitCommitSubFolderParam + " push " + gitRepositoryPath);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            break;

                        case "evaleachblock":
                            Pattern pattern = Pattern.compile("([ \\t]*)(@@@\\{([\\s\\S]*?)}@@@)");
                            Matcher matcher = pattern.matcher(newContent);

                            while (matcher.find()) {
                                String indentation = matcher.group(1);
                                String wholeBlock = matcher.group(2);
                                String internalBlock = matcher.group(3);
//                        internalBlock = Utils.removeColumns(internalBlock, indentation.length()).trim();
                                String blockNewContent = (String) this.autoEvalStringTransforms(internalBlock, history);
                                newContent = newContent.replace(wholeBlock, blockNewContent);
                            }
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

                        case "freemarker":
                            newContent = evalFreemarker(newContent);
                            break;

                        case "groovy":
                            String groovyScriptPath = null;
                            if (Utils.isDebugMode() && !parsedTransformParams.isEmpty()) {
                                String groovyFileName = (String) parsedTransformParams.getFirst();
                                var groovyScriptRelativePath = FileUtils.pathJoin("src/main/resources/executors/", groovyFileName);
                                if (!Files.exists(groovyScriptRelativePath)) {
                                    groovyScriptRelativePath = FileUtils.pathJoin("src/main/resources/transforms/", groovyFileName);
                                }
                                if (Files.exists(groovyScriptRelativePath)) {
                                    groovyScriptPath = groovyScriptRelativePath.toString();
                                }
                            }
                            newContent = String.valueOf(evalGroovy(Utils.nvl(groovyScriptPath, newContent)));
                            break;

                        case "mcp":
                            String mcpSubCommand = Utils.castOrDefault(Utils.getParam(parsedTransformParams, 0, null), String.class, null);
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

                                String agentNameParam = Utils.castOrDefault(Utils.getParam(parsedTransformParams, 1, null), String.class, null);
                                AgentDto agentDto = null;

                                if (agentNameParam != null && !agentNameParam.isBlank()) {
                                    String target = agentNameParam.trim();

                                    agentDto = agentRegistryService.find(target).orElse(null);
                                    if (agentDto != null) {
                                        log.info("MCP: Using agent '{}' resolved from registry by name '{}'", agentDto.getName(), agentNameParam);
                                    }

                                    if (agentDto == null) {
                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");
                                            if (recipe != null && recipe.get("config") instanceof Map<?, ?> config && ((Map<?, ?>) config).get("agents") instanceof List<?> agents) {
                                                @SuppressWarnings("unchecked")
                                                List<Map<String, Object>> agentsList = (List<Map<String, Object>>) ((Map<?, ?>) config).get("agents");
                                                Map<String, Object> selected = null;
                                                for (Map<String, Object> agentList : agentsList) {
                                                    Object nameObj = agentList.get("name");
                                                    if (nameObj != null) {
                                                        String name = String.valueOf(nameObj);
                                                        if (name.equalsIgnoreCase(target)) {
                                                            selected = agentList;
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (selected != null) {
                                                    agentDto = llmSpringService.parseAgentConfig(selected);
                                                    log.info("MCP: Using agent '{}' resolved from recipe by name '{}'", agentDto.getName(), agentNameParam);
                                                } else {
                                                    log.warn("MCP: Agent name '{}' not found in recipe. Using default agent", agentNameParam);
                                                }
                                            } else {
                                                log.warn("MCP: Recipe or recipe.config.agents not available to resolve agent '{}'. Using default MCP agent.", agentNameParam);
                                            }
                                        } catch (Exception e) {
                                            log.warn("MCP: Error while resolving agent '{}' from recipe. Using default MCP agent.", agentNameParam, e);
                                        }
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
                            newContent = handleAgent(projectContext, newContent, null);
                            break;

                        case "agent":
                            Map<String, Object> agentParams = llmSpringService.parseAgentParams(parsedTransformParams);
                            String agentName = (String) agentParams.get("agentName");
                            @SuppressWarnings("unchecked")
                            List<String> fileNames = (List<String>) agentParams.get("fileNames");
                            @SuppressWarnings("unchecked")
                            List<String> requestedTools = (List<String>) agentParams.get("requestedTools");

                            AgentDto agentDto = null;
                            if (agentName != null && !agentName.isBlank()) {
                                agentDto = agentRegistryService.find(agentName).orElse(null);
                                if (agentDto != null) {
                                    log.info("Agent: Using '{}' from registry by name '{}'", agentDto.getName(), agentName);
                                }
                            }

                            if (agentDto == null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");
                                Map<String, Object> config = (Map<String, Object>) recipe.get("config");

                                if (config != null && config.get("agents") instanceof List<?>) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> agents = (List<Map<String, Object>>) config.get("agents");
                                    Map<String, Object> agent = agents.stream()
                                            .filter(it -> agentName != null && agentName.equalsIgnoreCase(String.valueOf(it.get("name"))))
                                            .findFirst()
                                            .orElse(null);

                                    if (agent != null) {
                                        if (!requestedTools.isEmpty() && agent.get("tools") instanceof List<?>) {
                                            llmSpringService.filterAgentTools(agent, requestedTools);
                                        }
                                        Object parsed = (agent.get("isEmbedding") instanceof Boolean b && b)
                                                ? llmSpringService.parseEmbeddingAgentConfig(agent)
                                                : llmSpringService.parseAgentConfig(agent);

                                        if (parsed instanceof AgentDto) {
                                            agentDto = (AgentDto) parsed;
                                        } else if (parsed instanceof AgentEmbConfigDto) {
                                            Object originalAgent = projectContext.get("agent");
                                            //llmSpringService.ensureLlmThreadActive(projectContext, agentName);
                                            try {
                                                projectContext.put("agent", parsed);
                                                handleAgent(projectContext, newContent, fileNames);
                                            } finally {
                                                if (originalAgent != null) projectContext.put("agent", originalAgent);
                                                else projectContext.remove("agent");
                                            }
                                            break;
                                        }
                                    } else {
                                        log.warn("Agent: Agent name '{}' not found in recipe. Using default agent", agentName);
                                    }
                                }
                            }

                            if (agentDto == null) {
                                agentDto = llmSpringService.getDefaultConfig();
                                log.warn("Agent: Agent '{}' not found in registry or recipe. Using default agent '{}'.",
                                        agentName, agentDto.getName() != null ? agentDto.getName() : agentDto.getProvider());
                            }

                            if (!requestedTools.isEmpty() && agentDto.getTools() != null && !agentDto.getTools().isEmpty()) {
                                List<String> available = agentDto.getTools();
                                List<String> filtered = requestedTools.stream().filter(available::contains).toList();
                                if (!filtered.isEmpty()) {
                                    log.warn("Requested tools '{}' not available for agent '{}'. Available tools: {}", requestedTools, agentName, available);
                                } else {
                                    agentDto.setTools(filtered);
                                }
                            }

                            Object originalAgent = projectContext.get("agent");
                            llmSpringService.ensureLlmThreadActive(projectContext, agentName != null ? agentName : "agent");

                            try {
                                projectContext.put("agent", agentDto);
                                handleAgent(projectContext, newContent, fileNames);
                            } finally {
                                if (originalAgent != null) projectContext.put("agent", originalAgent);
                                else projectContext.remove("agent");
                            }
                            break;


//                        case "agentold":
//                            var agentName = evalIfSpEL(Utils.getParam(parsedTransformParams, 0, null));
//                            if (agentName != null && ((List<AgentDto>) projectContext.get("agents")).stream().filter(it -> it.getName().equals(agentName)).findFirst().orElse(null) instanceof AgentDto agentDto) {
//                                if (agentDto.getBefore() instanceof String agentBefore) {
//                                    newContent = (String) autoEval(agentBefore + "\n" + newContent);
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
//                                llmService.configure(agentDto.getUuid(), agentDto.getProvider(), agentDto.getModel(), agentDto.getVersion(), agentDto.getTemperature(), systemInstructions);
//
//                                var memory = "[REQUEST]\n" + newContent;
//                                newContent = handleAgent(newContent, agentDto.getUuid());
//                                memory += "\n\n[AGENT ANSWER]\n" + newContent;
//
//                                if (agentDto.getDuring() instanceof String agentDuring) {
//                                    newContent = (String) autoEval(agentDuring + "\n" + newContent);
//                                }
//
//                                if (agentDto.getTools() != null && !agentDto.getTools().isEmpty()) {
//                                    while (JsonUtils.isValidJson(newContent)) {
//                                        var toolsExecution = JsonUtils.readAsListOf(newContent, ToolDto.class);
//                                        for (var toolExecution : toolsExecution) {
//                                            var toolExecutionResult = toolsFunction.invokeToolMethod(toolExecution);
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
//                                    newContent = (String) autoEval(agentAfter + "\n" + newContent);
//                                }
//                            } else {
//                                throw new RuntimeException("Agent '" + agentName + "' not found!");
//                            }
//                            break;

                        case "repromptallmethods":
                            var extractedMarkdown = (String) Utils.extractMarkdownCode(newContent);
                            var listAllMethods = JavaUtils.listAllMethods(extractedMarkdown);
                            for (String method : listAllMethods) {
                                String repromptText = """
                                        1. Improve the method being aware of any DTO, REPOSITORY, ENTITY MODEL available at the context!
                                        2. Take advantage of the current existing comments inside of the original method below.
                                        3. The new content needs to fit the same method signature, don't generate other helper methods for it!
                                        4. Encapsulate all code within a try-catch block.
                                        5. Include a JavaDoc to the method.
                                        6. Include any import needed.
                                        7. Use a standard class/primitive type as return type or any of the DTOs listed.     \s
                                        8. You are not allowed to generate methods with more than 30 lines of code, whenever it gets over that size split the method in compreensive pieces without touching the original method\s
                                        signature that you are refactoring. Any of the additional methods needs to be declared as private.
                                        9. Remove any comments inside the method! They are not allowed!
                                        10. Every method that is supposed to change a value from another scope needs to return a value or save it on the respective repository/file already been used by the service.
                                        
                                        ORIGINAL METHOD CONTENT:
                                        """.concat(method);
                                String newMethodContent = (String) Utils.extractMarkdownCode(handleAgent(projectContext, repromptText, null));
                                newContent = newContent.replace(method, newMethodContent);
                            }
                            break;

                        case "repromptandreplacepoormethods":
                            var extractedMarkdown2 = (String) Utils.extractMarkdownCode(newContent);
                            var listOfPoorMethods = JavaUtils.listPoorMethods(extractedMarkdown2);
                            for (String poorMethod : listOfPoorMethods) {
                                String repromptText = """
                                        The method below is wrong or incomplete! Please return the full code for ONLY that method and wrap it on a markdown java code without including any other content to your answer, just the code!
                                        
                                        """.concat(poorMethod);
                                String newMethodContent = (String) Utils.extractMarkdownCode(handleAgent(projectContext, repromptText, null));
                                newContent = newContent.replace(poorMethod, newMethodContent);
                            }
                            break;

                        case "withoutclass":
                            newContent = JavaUtils.withoutClass(newContent);
                            break;

                        case "extractandwriteotherclasses":
                            String paths = evalIfSpEL(parsedTransformParams.getFirst());
                            var microservicePath = paths.split(",")[0];
                            var mainFilePath = paths.split(",")[1];
                            newContent = JavaUtils.extractAndWriteOtherClasses(microservicePath, mainFilePath, newContent);
                            break;

                        case "spel":
                            String expression = newContent;
                            if (!parsedTransformParams.isEmpty()) {
                                expression = (String) parsedTransformParams.getFirst();
                            }

                            Map<String, Object> tempContext = new ConcurrentLinkedHashMap<>();
                            tempContext.put("projectContext", projectContext);
                            tempContext.putAll(projectContext);

                            newContent = String.valueOf(evalSpEL(tempContext, expression));
                            break;

                        case "extractmarkdowncode":
                            newContent = (String) Utils.extractMarkdownCode(newContent);
                            break;

                        case "mapput":
                            var scriptService = applicationContext.getBean(ScriptService.class);

                            var mapPath = (String) parsedTransformParams.removeFirst();
                            var mapKey = (String) parsedTransformParams.removeFirst();

                            mapKey = scriptService.evalIfSpEL(mapKey);
                            var map = (Map<String, String>) Utils.anyCollectionGetOrSet(projectContext, mapPath, new ConcurrentLinkedHashMap<String, String>());
                            map.put(mapKey, newContent);
                            break;

                        case "skip":
                            String skipExpression = String.valueOf(parsedTransformParams.getFirst());
                            var isSkip = Utils.castOrDefault(evalSpEL(skipExpression), Boolean.class, Boolean.FALSE);
                            if (isSkip) {
                                if (!dontUpdateContent) {
                                    newContent = "// Skipped/Empty file";
                                }
                                break LB_WHILE;
                            }
                            break;

                        case "plantuml":
                            if (!parsedTransformParams.isEmpty()) {
                                String filePath = evalIfSpEL(parsedTransformParams.getFirst());
                                plantUMLService.writeToImg(filePath, newContent);
                            }

                            // TODO: Review a redundant call to plantuml service
                            newContent = plantUMLService.writeToBase64(newContent);
                            break;

                        case "includeallmissingclassvariables":
                            newContent = includeMissingVariableDeclarations(projectContext, newContent, parsedTransformParams);
                            break;

                        case "fill":
                            var fillObject = evalIfSpEL(parsedTransformParams.getFirst());
                            Integer fillBatchSize = evalIfSpEL(Utils.getParam(parsedTransformParams, 1, 1000));

                            if (fillObject instanceof String fillString) {

                            } else if (fillObject instanceof Map<?, ?> fillMap) {
                                while (true) {
                                    var traversedMap = new ConcurrentLinkedHashMap<String, Object>();
                                    Utils.traverseJsonPath(traversedMap, fillMap, "", key -> key);
                                    var listOfItemsToBeFilled = traversedMap.entrySet().stream().filter(entry -> {
                                                var value = entry.getValue();
                                                if (value instanceof String vString) {
                                                    var trimmed = vString.trim();
                                                    return trimmed.startsWith("!!") && trimmed.endsWith("!!");
                                                }

                                                return false;
                                            })
                                            .map(it -> {
                                                var k = it.getKey();
                                                String v = ((String) it.getValue()).trim();
                                                String placeHolderContent = v.substring(2, v.length() - 2);

                                                return "\"" + k + "\": \"" + placeHolderContent + "\"";
                                            })
                                            .toList();

                                    var batchOfItemsToBeFilled = listOfItemsToBeFilled.subList(0, Math.min(listOfItemsToBeFilled.size(), fillBatchSize));

                                    var fillTemplate = String.join(",\n", batchOfItemsToBeFilled);

                                    String prompt = """
                                            Given the [CONTEXT] below, analyze and answer with a JSON OBJECT following the [TEMPLATE].
                                            You have to fill each item of the [TEMPLATE] using the same keys, but changing the values because the current ones are
                                            just guidelines of what its expected to be filled on it.
                                            
                                            [TEMPLATE]
                                            {
                                            $template
                                            }
                                            
                                            [CONTEXT]
                                            $context
                                            """.replace("$template", fillTemplate);
//                                           .replace("$context", fillContext);

                                    int fillAttempts = 0;
                                    int fillFailsLimit = 3;
                                    boolean successfulBatch = false;
                                    Map<String, Object> responseJson = new ConcurrentLinkedHashMap<>();
                                    do {
                                        if (fillAttempts >= fillFailsLimit) {
                                            throw new IllegalStateException("Same batch of @@@fill failed " + fillAttempts + " times already!");
                                        }

                                        fillAttempts++;

                                        String fillResponse = handleAgent(projectContext, prompt, null);
                                        String extractFillResponse = Extractors.extract(this, List.of("REGEX_MARKDOWN_BIGGEST"), fillResponse, projectContext);
                                        if (!JsonUtils.isValidJson(extractFillResponse)) {
                                            continue;
                                        }

                                        responseJson = JsonUtils.readAsMap(extractFillResponse);
                                        if (!fillMap.keySet().containsAll(responseJson.keySet())) {
                                            continue;
                                        }

                                        successfulBatch = true;
                                    } while (!successfulBatch);

                                    ((Map<String, Object>) fillMap).putAll(responseJson);
                                }
                            } else if (fillObject instanceof Collection<?> fillCollection) {

                            } else {
                                throw new IllegalArgumentException("Fill can only deal with String, Map or List");
                            }
                            break;

                        case "nodify":
                            var nodified = Transforms.nodify(newContent);
                            newContent = JsonUtils.writeAsJsonString(nodified, true);
                            break;

                        case "neo4j":
                            String neo4jURL = (String) autoEval((String) parsedTransformParams.getFirst());
                            String neo4jAUTH = (String) autoEval((String) parsedTransformParams.get(1));

                            Map<String, String> headers = Map.of("Authorization", neo4jAUTH, "Content-Type", "application/json");

                            Map<String, Object> body = new ConcurrentLinkedHashMap<>();
                            var statements = new ConcurrentLinkedList<>();
                            body.put("statements", statements);

                            if (JsonUtils.isValidJson(newContent)) {
                                var neo4jJsonSource = JsonUtils.readAsObject(newContent, new ConcurrentLinkedList<>());

                                List<Map<String, Object>> neo4jUpdates = new ConcurrentLinkedList<>();

                                if (neo4jJsonSource instanceof Map<?, ?> neo4jMapSource) {
                                    for (Map<String, Object> node : (List<Map<String, Object>>) neo4jMapSource.get("nodes")) {
                                        node.put("type", "node");
                                        neo4jUpdates.add(node);
                                    }

                                    for (Map<String, Object> relationship : (List<Map<String, Object>>) neo4jMapSource.get("relationships")) {
                                        relationship.put("type", "relationship");
                                        neo4jUpdates.add(relationship);
                                    }

                                    //neo4jUpdates.add((Map<String, Object>) neo4jMapSource);
                                } else if (neo4jJsonSource instanceof Collection<?> neo4jCollectionSource) {
                                    neo4jUpdates.addAll((Collection<? extends Map<String, Object>>) neo4jCollectionSource);
                                } else {
                                    throw new IllegalArgumentException("Invalid neo4j JSON reference!");
                                }

                                neo4jUpdates.forEach(it -> {
                                    String type = Utils.nvl(it.get("type"), "node").toString();

                                    if (Objects.equals(type, "node")) {
                                        var labels = new ConcurrentLinkedList<>();
                                        if (it.get("labels") instanceof Collection<?> labelsList) {
                                            labels.addAll(labelsList);
                                        } else if (it.get("label") instanceof String labelString) {
                                            labels.add(labelString);
                                        } else {
                                            throw new IllegalArgumentException("Invalid neo4j label neither labels list!");
                                        }

                                        var skip = List.of("key", "label", "labels");
                                        List<String> vals = new ConcurrentLinkedList<>();
                                        it.forEach((k, v) -> {
                                            if (!skip.contains(k)) {
                                                var textValue = JsonUtils.writeAsJsonString(v, true);
                                                vals.add("n." + k + " = " + textValue);
                                            }
                                        });

                                        var valsString = String.join(",\n", vals);

                                        String statement = """
                                                MERGE (n:$label {key: "$key"})
                                                ON CREATE SET
                                                  $vals
                                                ON MATCH SET
                                                  $vals
                                                """.replace("$label", (String) labels.getFirst())
                                                .replace("$key", (String) it.get("key"))
                                                .replace("$vals", valsString);

                                        statements.add(Map.of("statement", statement));
                                    } else if (Objects.equals(type, "relationship")) {

                                    } else {
                                        throw new IllegalArgumentException("Invalid neo4j JSON item type: " + type);
                                    }
                                });
                            } else {
                                statements.add(Map.of("statement", newContent));
                            }

                            var neo4jResult = toolsFunction.apiCall(neo4jURL, "POST", body, headers);
                            newContent = JsonUtils.writeAsJsonString(neo4jResult, true);
                            break;

                        case "api":
                            String apiURL = evalIfSpEL(parsedTransformParams.getFirst());
                            String apiMethod = evalIfSpEL(parsedTransformParams.get(1));
                            Object apiBody = parsedTransformParams.size() > 2 ? evalIfSpEL(parsedTransformParams.get(2)) : null;
                            Map<String, String> apiHeader = parsedTransformParams.size() > 3 ? evalIfSpEL(parsedTransformParams.get(3)) : null;

                            var apiResponse = toolsFunction.apiCall(apiURL, apiMethod, apiBody, apiHeader);
                            if (apiResponse instanceof String apiResponseString) {
                                newContent = apiResponseString;
                            } else {
                                newContent = JsonUtils.writeAsJsonString(apiResponse, true);
                            }
                            break;

                        case "jolt":
                            String spec = (String) autoEval((String) parsedTransformParams.getFirst());
                            var transformed = toolsFunction.jolt(newContent, spec);
                            newContent = (transformed instanceof String transformedString) ? transformedString : JsonUtils.writeAsJsonString(transformed, true);
                            break;

                        case "encodebase64":
                            newContent = Utils.encodeBase64(newContent);
                            break;

                        case "decodebase64":
                            newContent = Utils.decodeBase64ToString(newContent);
                            break;

                        case "default":
                            defaultExpression = (String) parsedTransformParams.getFirst();
                            break;

                        case "retry":
                            attempts = (Integer) parsedTransformParams.getFirst();
                            retryCheckpointExpression = parsedTransformParams.size() > 1 ? (String) parsedTransformParams.get(1) : "";

                            retryCheckpointIndex = transformIndex;
                            retryCheckpointContent = newContent;
                            break;

                        case "exec":
                            String template = (String) parsedTransformParams.getFirst();
                            if (isValidSpEL(template)) {
                                template = String.valueOf(evalSpEL(template));
                            } else {
                                log.warn("Raw template will be executed, as the parameter was not identified as a valid SpEL content!");
                            }

                            newContent = String.valueOf(autoEval(template, history));
                            break;

                        case "ask":
                            String key = String.valueOf(parsedTransformParams.removeFirst());
                            String question = String.valueOf(parsedTransformParams.removeFirst());

                            List<Map<String, Object>> askQueue = (List<Map<String, Object>>) projectContext.computeIfAbsent("askQueue", k -> new ConcurrentLinkedList<Map<String, Object>>());
                            Map<String, Object> ask = new ConcurrentLinkedHashMap<>();
                            askQueue.add(ask);

                            ask.put("key", key);
                            ask.put("type", parsedTransformParams.size() <= 1 ? "INPUT" : "BUTTONS");
                            ask.put("label", question);
                            List<String> askValues = (List<String>) ask.computeIfAbsent("values", k -> new ConcurrentLinkedList<String>());

                            if (parsedTransformParams.size() > 1) {
                                while (!parsedTransformParams.isEmpty()) {
                                    askValues.add(evalIfSpEL(parsedTransformParams.removeFirst()));
                                }
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

                        case "cache":
                            if (!projectContext.containsKey("cacheMap")) {
                                projectContext.put("cacheMap", new ConcurrentLinkedHashMap<>());
                            }

                            Map<String, Object> cacheMap = (ConcurrentLinkedHashMap<String, Object>) projectContext.get("cacheMap");
                            cachePath = (String) projectContext.get("filePath");
                            cacheHash = Utils.hashString(content + newContent);
                            if (cacheMap.get(cachePath) instanceof Map<?, ?> cacheItemMap && cacheItemMap.get("hash") instanceof String cacheItemHash && cacheItemHash.equals(cacheHash)) {
                                newContent = (String) cacheItemMap.get("content");
                                if (cacheItemMap.get("vars") instanceof Map<?, ?> cacheVars) {
                                    var cacheVarsMap = (Map<String, Object>) cacheVars;
                                    cacheVarsMap.forEach((k, v) -> {
                                        Utils.anyCollectionSet(projectContext, k, v);
                                    });
                                }
                                transformIndex = transformLines.size();
                            } else {
                                cacheDataList.addAll(parsedTransformParams);
                            }
                            break;

                        case "case":
                            var caseParamsPairs = Utils.listGroupsOf(parsedTransformParams, 2);

                            for (var caseParamsPair : caseParamsPairs) {
                                var caseParamsPairConditionOrDefault = Utils.getParam(caseParamsPair, 0, null);

                                if (caseParamsPair.size() == 1) {
                                    newContent = (String) autoEval((String) caseParamsPairConditionOrDefault, history);
                                    break;
                                }


                                String caseParamsPairTemplate = Utils.getParam(caseParamsPair, 1, "");

                                if ((boolean) evalIfSpEL(caseParamsPairConditionOrDefault)) {
                                    newContent = (String) autoEval(caseParamsPairTemplate, history);
                                    break;
                                }
                            }
                            break;

                        case "schema":
                            String schema = evalIfSpEL(Utils.getParam(parsedTransformParams, 0, null));
                            String errorsTarget = evalIfSpEL(Utils.getParam(parsedTransformParams, 1, null));
                            boolean throwException = evalIfSpEL(Utils.getParam(parsedTransformParams, 2, false));
//                        if (XmlUtils.isValidXml(newContent)) {
//                            var aasd = JsonUtils.getErrorsAgainstJsonSchema(JsonUtils.writeAsJsonString(XmlUtils.readAs(newContent, Object.class), true));
//                        }
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

                        case "checkpoint":
                            String checkpointName = evalIfSpEL(parsedTransformParams.getFirst());
                            var checkpoint = CheckpointDto.builder()
                                    .name(checkpointName)
                                    .transformIndex(transformIndex)
                                    .content(newContent)
                                    .build();

                            checkpointMap.put(checkpointName, checkpoint);
                            break;

                        case "failif":
                            Object failIfExpression = parsedTransformParams.getFirst();

                            String exceptionMessage = null;
                            if (parsedTransformParams.size() > 1) {
                                var evaluated = evalIfSpEL(parsedTransformParams.get(1));
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

                        case "goto":
                            String gotoCheckpointName = evalIfSpEL(parsedTransformParams.getFirst());

                            if (checkpointMap.get(gotoCheckpointName) instanceof CheckpointDto gotoCheckpoint) {
                                transformIndex = gotoCheckpoint.getTransformIndex();
                                newContent = gotoCheckpoint.getContent();
                            }
                            break;

                        case "repeat":
                            Object repeatItems = evalIfSpEL(parsedTransformParams.getFirst());
                            String repeatItemPath = Utils.getParam(parsedTransformParams, 1, null);
                            String repeatTemplate = Utils.getParam(parsedTransformParams, 2, null);

                            int repeatItemIndex = 0;
                            if (repeatItems instanceof Map<?, ?> repeatItemsMap) {
                                for (var entry : repeatItemsMap.entrySet()) {
                                    Utils.anyCollectionSet(projectContext, repeatItemPath, entry);
                                    autoEval(repeatTemplate, history);
                                    projectContext.put("repeatItemIndex", repeatItemIndex++);
                                }
                            } else if (repeatItems instanceof List<?> repeatItemsList) {
                                for (var item : repeatItemsList) {
                                    Utils.anyCollectionSet(projectContext, repeatItemPath, item);
                                    autoEval(repeatTemplate, history);
                                    projectContext.put("repeatItemIndex", repeatItemIndex++);
                                }
                            } else if (repeatItems instanceof Integer repeatItemsInteger) {
                                for (int i = 0; i < repeatItemsInteger; i++) {
                                    Utils.anyCollectionSet(projectContext, repeatItemPath, repeatItems);
                                    autoEval(repeatTemplate, history);
                                    projectContext.put("repeatItemIndex", repeatItemIndex++);
                                }
                            } else {
                                Utils.anyCollectionSet(projectContext, repeatItemPath, repeatItems);
                                autoEval(repeatTemplate, history);
                                projectContext.put("repeatItemIndex", repeatItemIndex++);
                            }

                            projectContext.remove("repeatItemIndex");
                            break;

                        case "extract":
                            newContent = Extractors.extract(this, parsedTransformParams, newContent, projectContext);
                            break;

                        case "shell":
                            List<String> stringParams = new ConcurrentLinkedList<>();
                            for (var s : parsedTransformParams) {
                                var paramResult = evalIfSpEL(s);
                                if (paramResult instanceof String paramResultString) {
                                    stringParams.add(paramResultString);
                                }
                            }
                            stringParams.add("\"" + newContent.replace("\"", "\\\"").replaceAll("[\r?\n]+", "; ") + "\"");

                            var command = String.join(" ", stringParams);
                            newContent = toolsFunction.shellRun(null, command);
                            break;

                        case "log":
                            String logExpression = Utils.getParam(parsedTransformParams, 0, null);
                            String logMessage = evalIfSpEL(logExpression);
                            log.info(logMessage);
                            break;

                        case "parseandapply":
                            var extractParams = Utils.splitParams(transformParams);
                            String parserLanguageString = Utils.getParam(extractParams, 0, null);

                            var parsedContent = ParserUtils.parse((Map<String, Grammar>) projectContext.get("grammars"), parserLanguageString, newContent);

                            Object result = Extractors.applyExtractions(this, parsedContent, projectContext, extractParams.subList(1, extractParams.size()));

                            if (result instanceof String resultString) {
                                newContent = resultString;
                            } else {
                                newContent = JsonUtils.writeAsJsonString(result, true);
                            }

                            //@@@_exec("${#recipe['templates']['javaRepo']}")
                            //javaRepo:
                            //@@@_parse("JAVA", "...", "...")
                            //@@@_failIf("${...}")

                            //JsonPath.read((Object) JsonPath.read(jsonTree.toString(), "$..[?(@.name == 'classBodyDeclaration' && @..children[?(@.name == 'modifier' && @.text == '@Id')].length() > 0)]..[?(@.name == 'typeType')].text"), "$[0]")
                            break;

                        default:
                            if (transformCommand.startsWith("superutils.")) {
                                String superUtilsMethodName = transformCommand.substring(11);
                                Method superUtilsMethod = Arrays.stream(SuperUtils.class.getDeclaredMethods()).filter(it -> it.getName().equals(superUtilsMethodName) && it.getParameterCount() == 1).findFirst().orElse(null);
                                if (superUtilsMethod != null) {
                                    newContent = (String) superUtilsMethod.invoke(null, newContent);
                                } else {
                                    log.error("Didn't found any match for the expected transform function: {}", transformCommand);
                                }
                            } else if (transformCommand.startsWith("utils.")) {
                                String utilsMethodName = transformCommand.substring(6);
                                Method utilsMethod = Arrays.stream(Utils.class.getDeclaredMethods()).filter(it -> it.getName().equals(utilsMethodName) && it.getParameterCount() == 1).findFirst().orElse(null);
                                if (utilsMethod != null) {
                                    newContent = (String) utilsMethod.invoke(null, newContent);
                                } else {
                                    log.error("Didn't found any match for the expected transform function: {}", transformCommand);
                                }
                            } else {
                                String finalTransformCommand = transformCommand;
                                String customTransformKey = CUSTOM_TRANSFORMS.keySet().stream().filter(it -> it.equalsIgnoreCase(finalTransformCommand)).findFirst().orElse(null);
                                if (customTransformKey != null && CUSTOM_TRANSFORMS.get(customTransformKey) instanceof ITransform groovyCustomTransform) {
                                    newContent = groovyCustomTransform.execute(applicationContext, projectContext, newContent, transformParams);
                                }
                            }
                    }

                    if (isCacheable) {
                        log.info("Cache updated");
                        tempCache.put(transformCacheHash, newContent);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex;
                    while (cause != null) {
                        if (InterruptedException.class.isAssignableFrom(cause.getClass())) {
                            log.error("A forced interruption of the execution was requested! Message: {}", cause.getMessage());
                            throw ex;
                        }

                        cause = cause.getCause();
                    }

                    long end = System.currentTimeMillis();
                    transformDto.setMillisSpent(end - start);

                    if (attempts-- > 0) {
                        transformIndex = retryCheckpointIndex;

                        log.warn("Something went wrong trying to execute a '{}', but there's a retry checkpoint to be followed before dismiss! Input parameters:\n{}", transformCommand, parsedTransformParams);
                        dontUpdateContent = false;
                        if (isValidSpEL(retryCheckpointExpression)) {
                            try {
                                newContent = (String) evalSpEL(retryCheckpointExpression);
                            } catch (Exception ex2) {
                                log.error("Failed to evaluate checkpointRetryExpression: {}\nConsidering the original checkpointContent as reference!", retryCheckpointExpression);
                            }
                        } else {
                            newContent = retryCheckpointContent;
                        }
                    } else {
                        history.add(
                                TransformDto.builder()
                                        .name("Exception")
                                        .content(ex.getMessage() + "\n\n\n" + Utils.getStackTraceAsString(ex))
                                        .build()
                        );

                        if (defaultExpression != null) {
                            log.warn("Using the last 'default' configured to set the value!");
                            dontUpdateContent = false;
                            newContent = String.valueOf(autoEval(defaultExpression));
                            transformIndex = transformLines.size();
                            var defaultFinalTransformDto = TransformDto.builder().name("default").content(newContent).build();
                            history.add(defaultFinalTransformDto);
                        } else {
                            throw ex;
                        }
                    }
                }
            }

            String finalNewContent = newContent;

            transformSplitted.stream().filter(it -> it.startsWith(":")).map(it -> it.substring(1)).forEach(it ->
                    Utils.anyCollectionSet(projectContext, it, finalNewContent)
            );

            if (dontUpdateContent) {
                newContent = backupContent;
            }

            projectContext.put("content", newContent);

            long end = System.currentTimeMillis();
            transformDto.setMillisSpent(end - start);
            transformDto.getCaches().addAll(tempCache.keySet());
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

//        var finalTransforDto = TransformDto.builder()
//                .name("Final")
//                .content(newContent)
//                .build();
//        history.add(finalTransforDto);

        return newContent;
    }


    public String handleAgent(Map<String, Object> projectContext, String content, List<String> fileNames) throws Exception {
        return handleAgent(projectContext, content, fileNames, 3);
    }

    public String handleAgent(Map<String, Object> projectContext, String content, List<String> fileNames, int retries) throws Exception {
        Object config = projectContext.get("agent");
        if (config == null) {
            log.warn("No agent config found, using default prompt!");
            config = llmSpringService.getDefaultConfig();
        } else if (config instanceof String alias && !alias.isBlank()) {
            Map<String, Object> aliasMap = new HashMap<>();
            aliasMap.put("name", alias);
            config = llmSpringService.parseAgentConfig(aliasMap);
            projectContext.put("agent", config);
        }
        Map<String, Object> files = llmSpringService.getFilesFromContext(projectContext);
        if (files == null || files.isEmpty() && fileNames != null && !fileNames.isEmpty()) {
            throw new IllegalStateException("No files found in context, but file names were provided in agent config!");
        }

        final Object finalConfig = config;

        //llmSpringService.ensureLlmThreadActive(projectContext, "agent");
        String threadId = (String) projectContext.get(CONSTANT_LLM_THREAD_KEY);

        do {
            try {
                if (finalConfig instanceof AgentEmbConfigDto embeddingConfig) {
                    String result = Arrays.deepToString(llmEmbeddingSpringService.promptEmbeddingAsArray(content, embeddingConfig));
                    log.debug("LLM Response: \n{}", result);
                    return result;
                } else if (finalConfig instanceof AgentDto agentConfig) {
                    if (fileNames == null || fileNames.isEmpty() || files == null || files.isEmpty()) {
                        String result = llmSpringService.prompt(content, agentConfig, threadId);
                        log.debug("LLM Response: {}", result);
                        return result;
                    }

                    boolean urlOnly = llmSpringService.isUrlOlyMode(projectContext, agentConfig);
                    if (urlOnly || fileNames == null || fileNames.isEmpty() || files == null || files.isEmpty()) {
                        String result = llmSpringService.prompt(content, agentConfig, threadId);
                        log.debug("LLM Response: {}", result);
                        return result;
                    }

                    List<MultipartFile> multipartFiles = llmSpringService.prepareMultipartFiles(files, fileNames);
                    String result = llmSpringService.promptWithFile(content, multipartFiles, agentConfig, threadId);
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

    public Object autoEval(String content) throws Exception {
        return autoEval(content, new ConcurrentLinkedList<>());
    }

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

                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        var keyExpr = String.valueOf(entry.getKey());
                        List<String> params = (List<String>) Utils.convertToConcurrent(Arrays.stream(keyExpr.split("\\|(?![^$]*})")).toList());
                        var parentRelationshipAndKey = params.removeFirst().split(":");
                        var key = (parentRelationshipAndKey.length > 1 ? parentRelationshipAndKey[1] : parentRelationshipAndKey[0]).trim();
                        var nextRelationship = (parentRelationshipAndKey.length > 1 ? parentRelationshipAndKey[0] : "CONTAINS").trim();

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
}
