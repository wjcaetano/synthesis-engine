package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.SynthesisEngineApplication;
import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.dto.TaskMap;
import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.dto.grammars.Grammar;
import com.capco.brsp.synthesisengine.dto.grammars.GrammarDto;
import com.capco.brsp.synthesisengine.enums.EnumEvaluateTypes;
import com.capco.brsp.synthesisengine.flow.EnumTaskStatus;
import com.capco.brsp.synthesisengine.flow.Flow;
import com.capco.brsp.synthesisengine.flow.Task;
import com.capco.brsp.synthesisengine.utils.*;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.aspectj.util.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

import static com.capco.brsp.synthesisengine.utils.Utils.convertToConcurrent;

@Slf4j
@RequiredArgsConstructor
@Service(value = "synthesisEngineService")
public class SynthesisEngineService {
    private static final Path EXECUTORS_PATH;
    private static final Path RECIPES_PATH;

    static {
        try {
            var executorsURL = SynthesisEngineApplication.class.getClassLoader().getResource("executors");
            if (Utils.isDebugMode() && executorsURL != null) {
                var executorsURLString = executorsURL.toURI().toString();
                executorsURLString = executorsURLString.replace("/target/classes", "/src/main/resources");
                EXECUTORS_PATH = Paths.get(new URI(executorsURLString));
            } else {
                EXECUTORS_PATH = null;
            }

            var recipesURL = SynthesisEngineApplication.class.getClassLoader().getResource("recipes");
            if (Utils.isDebugMode() && recipesURL != null) {
                var recipesURLString = recipesURL.toURI().toString();
                recipesURLString = recipesURLString.replace("/target/classes", "/src/main/resources");
                RECIPES_PATH = Paths.get(new URI(recipesURLString));
            } else {
                RECIPES_PATH = null;
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ApplicationContext applicationContext;
    private final ScriptService2 scriptService;
    @Autowired
    @Qualifier("contextService")
    private ContextService contextService;
    @Autowired
    @Qualifier("llmSpringService")
    private LLMSpringService llmSpringService;

    public String execute(UUID projectUUID, String contextKey, EnumEvaluateTypes enumEvaluateTypes, String relativeFilePath, String expression) throws Exception {
        var flowKey = contextService.setFlowKey(projectUUID, contextKey);

        var projectContext = contextService.getProjectContext();
        var adjusmentContext = new ConcurrentLinkedHashMap<>();
        projectContext.put("_adjustment", adjusmentContext);

        adjusmentContext.put("expression", expression);

        var normalizedRelativeFilePath = Paths.get(relativeFilePath).toString();
        var fullFilePath = FileUtils.absolutePathJoin(FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH, flowKey, normalizedRelativeFilePath);
        var fullFilePathString = fullFilePath.toString();
        adjusmentContext.put("filePath", fullFilePathString);
        expression = expression.replace("{{filePath}}", fullFilePathString);

        File file = new File(fullFilePathString);
        if (file.exists()) {
            var fileContent = Files.readString(fullFilePath);
            adjusmentContext.put("fileContent", fileContent);
            expression = expression.replace("{{fileContent}}", fileContent);
        }

        switch (enumEvaluateTypes) {
            case FREEMARKER:
                expression = "@@@freemarker\n" + expression;
                break;

            case GROOVY:
                expression = "@@@groovy\n" + expression;
                break;

            case PROMPT:
                expression = "@@@prompt\n" + expression;
                break;

            case SPEL:
                expression = "@@@spel\n" + expression;
                break;

            case TRANSFORM:
                break;

            default:
                throw new BadRequestException("Invalid expression type!");
        }

        List<TransformDto> fileHistory;
        if (projectContext.get("files_metadata") instanceof Map<?, ?> allMetadataMap && allMetadataMap.get(normalizedRelativeFilePath) instanceof Map<?, ?> fileMetadataMap) {
            if (fileMetadataMap.get("history") instanceof List<?> history) {
                fileHistory = (List<TransformDto>) history;
            } else {
                log.error("Failed to find get the history of the file: {}", normalizedRelativeFilePath);
                return flowKey;
            }
        } else {
            log.error("Failed to find the metadata of the file: {}", normalizedRelativeFilePath);
            return flowKey;
        }

        var evaluatedValue = scriptService.autoEvalStringTransforms(expression, fileHistory);

        final String finalFileValue;
        if (evaluatedValue instanceof String evaluatedValueString) {
            finalFileValue = evaluatedValueString;
        } else {
            finalFileValue = JsonUtils.writeAsJsonString(evaluatedValue, true);
        }

        Files.writeString(fullFilePath, finalFileValue);

        return flowKey;
    }

    public Map<String, Object> readRecipe(String recipeContent) throws Exception {
        var recipe = YamlUtils.readYAMLContent(recipeContent);
        if (recipe.get("recipe") instanceof String recipeReferenceString) {
            String recipeRefPathString = FileUtils.absolutePathJoin(RECIPES_PATH, recipeReferenceString).toString();
            if (!recipeReferenceString.contains(".")) {
                recipeRefPathString += ".yaml";
            }
            File recipeRefFile = new File(recipeRefPathString);
            if (!recipeRefFile.exists()) {
                throw new FileNotFoundException("Didn't found the recipe file at '" + recipeRefPathString + "'");
            }

            recipeContent = FileUtil.readAsString(recipeRefFile);
            recipe = YamlUtils.readYAMLContent(recipeContent);
        }

        return recipe;
    }

    public String evaluate(EnumEvaluateTypes enumEvaluateTypes, Map<String, Object> context, String expression) throws Exception {
        var evaluatedValue = switch (enumEvaluateTypes) {
            case FREEMARKER -> scriptService.evalFreemarker(context, expression);
            case GROOVY -> scriptService.evalGroovy(expression);
            case PROMPT -> llmSpringService.prompt(expression, (AgentDto) context);
            case SPEL -> scriptService.evalSpEL(context, expression);
            case TRANSFORM -> scriptService.autoEvalStringTransforms(expression);
        };

        if (evaluatedValue instanceof String evaluatedValueString) {
            return evaluatedValueString;
        }

        return JsonUtils.writeAsJsonString(evaluatedValue, true);
    }

    public Flow code(UUID projectUUID, String contextKey, String recipeContent, Map<String, Object> recipe, Map<String, Object> configs, Map<String, String> files) throws Exception {
        int contentHash = Objects.hash(recipeContent, configs, files);

        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        if (Flow.PROJECT_FLOW.get(flowKey) instanceof Flow currentFlow) {
            switch (currentFlow.getStatus()) {
                case EnumTaskStatus.RUNNING:
                    return currentFlow;
                case EnumTaskStatus.INTERRUPTED:
                    currentFlow.executeAsync();
                    return currentFlow;
                case EnumTaskStatus.ERROR:
                    if (contentHash == currentFlow.getContentHash()) {
                        if (currentFlow.getCurrentTask() instanceof Task currentTask && currentTask.getStatus() != EnumTaskStatus.COMPLETE) {
                            currentTask.setStatus(EnumTaskStatus.NEW);
                        }

                        currentFlow.executeAsync();
                        return currentFlow;
                    }

                    log.error("Something change since the last call, so the Flow with '{}' flowKey will be recreated!", flowKey);
                    Flow.PROJECT_FLOW.remove(flowKey);
                    break;
                default:
                    log.error("None Flow was found with '{}' flowKey.", flowKey);
                    Flow.PROJECT_FLOW.remove(flowKey);
                    break;
            }
        }

        var concurrentRecipe = convertToConcurrent(recipe);

        var rootFolder = FileUtils.absolutePathJoin(FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH, flowKey);
        var isFresh = Boolean.parseBoolean(String.valueOf(Utils.anyCollectionGet(concurrentRecipe, "config.fresh")));
        if (isFresh) {
            contextService.startNewProjectContextKey(projectUUID, contextKey);
            FileUtils.recreateEmptyIfExist(rootFolder);
        }

        if (contextService.getProjectContext() == null) {
            var projectContextBackupPath = FileUtils.absolutePathJoin(rootFolder, "meta", "projectContext");
            if (FileUtils.isFileExists(projectContextBackupPath) && !isFresh) {
                String projectContextBackupString = FileUtil.readAsString(new File(projectContextBackupPath.toString()));
                var projectContext = (ConcurrentLinkedHashMap<String, Object>) Utils.convertToConcurrent(JsonUtils.readAsMap(projectContextBackupString));
                contextService.loadProjectContext(flowKey, projectContext);
            } else {
                contextService.startNewProjectContextKey(projectUUID, contextKey);
            }
        }
        var projectContext = contextService.getProjectContext();
        projectContext.put("recipe", concurrentRecipe);

        var apiContext = Utils.convertToConcurrent(Map.of(
                "configs", configs,
                "files", files
        ));
        projectContext.put("$api", apiContext);

        List<AgentDto> agents = new ConcurrentLinkedList<>();
        if (recipe.get("config") instanceof Map<?, ?> configMap) {
            if (configMap.get("agents") instanceof List<?> agentsList) {
                var agentsListString = JsonUtils.writeAsJsonString(agentsList, true);
                List<AgentDto> listOfAgentDto = JsonUtils.readAsListOf(agentsListString, AgentDto.class);
                agents.addAll(listOfAgentDto);
            }
        }
        projectContext.put("agents", agents);

        Map<String, Grammar> projectContextGrammars = new ConcurrentLinkedHashMap<>();
        Map<String, GrammarDto> recipeGrammars = recipe.get("grammars") instanceof Map<?, ?> scriptsMap
                ? JsonUtils.readAsMap(JsonUtils.writeAsJsonString(scriptsMap, true), GrammarDto.class)
                : new ConcurrentLinkedHashMap<>();
        recipeGrammars.forEach((grammarKey, grammarDto) -> {
            try {
                String entryRule = scriptService.evalIfSpEL(grammarDto.getEntryRule());

                String lexerBody = scriptService.evalIfSpEL(grammarDto.getLexer().getBody());
                if (Utils.isDebugMode() && grammarDto.getLexer().getReference() instanceof String lexerReference) {
                    var lexerPath = FileUtils.pathJoin("src/main/resources/antlr4/", lexerReference);
                    log.warn("[GRAMMARS] File '{}' will be used in place of the Lexer body!", lexerPath);
                    lexerBody = Files.readString(lexerPath);
                }

                String parserBody = scriptService.evalIfSpEL(grammarDto.getParser().getBody());
                if (Utils.isDebugMode() && grammarDto.getParser().getReference() instanceof String parserReference) {
                    var parserPath = FileUtils.pathJoin("src/main/resources/antlr4/", parserReference);
                    log.warn("[GRAMMARS] File '{}' will be used in place of the Grammar body!", parserPath);
                    parserBody = Files.readString(parserPath);
                }

                Map<String, String> solvedDependencies = new ConcurrentLinkedHashMap<>();
                grammarDto.getDependencies().forEach((k, v) -> {
                    var kk = (String) scriptService.evalIfSpEL(k);
                    var vv = (String) scriptService.evalIfSpEL(v);

                    solvedDependencies.put(kk, vv);
                });

                Map<String, String> extraG4s = new ConcurrentLinkedHashMap<>();
                grammarDto.getDependencies().forEach((extraKey, extraGrammarScriptDto) -> {
                    String extraEvaluatedKey = scriptService.evalIfSpEL(extraKey);
                    String extraBody = scriptService.evalIfSpEL(extraGrammarScriptDto.getBody());
                    if (Utils.isDebugMode() && extraGrammarScriptDto.getReference() instanceof String extraReference) {
                        var extraPath = FileUtils.pathJoin("src/main/resources/antlr4/", extraReference);
                        log.warn("[GRAMMARS-DEPENDENCIES] File '{}' will be used in place of the Extra body!", extraPath);
                        try {
                            extraBody = Files.readString(extraPath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    extraG4s.put(extraEvaluatedKey, extraBody);
                });

                projectContextGrammars.put(grammarKey, ParserUtils.prepareGrammar(grammarKey, grammarDto.getEngine(), lexerBody, parserBody, entryRule, extraG4s));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        projectContext.put("grammars", projectContextGrammars);

        projectContext.put("rootFolder", rootFolder.toString());

        log.info("flowKey        = {}", flowKey);
        log.info("USER_TEMP_PATH = {}", FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH);
        var progressCustomDataExpression = String.valueOf(Utils.nvl(Utils.anyCollectionGet(concurrentRecipe, "config.progressCustomData"), "")).trim();
        projectContext.put("$progressCustomDataExpression", progressCustomDataExpression);

        String executor = JsonPath.read(concurrentRecipe, "$.executor");

        boolean debugMode = Utils.isDebugMode();
        String executionMode = debugMode ? "DEBUG" : "PROD";

        if (!executor.contains("\n")) {
            if (executor.endsWith(".groovy")) {
                if (!Utils.isDebugMode()) {
                    throw new IllegalArgumentException("In production, the whole executor script should be included on the recipe, not only the reference!");
                }
                executor = FileUtils.absolutePathJoin(EXECUTORS_PATH, executor).toString();
                log.warn("Execution mode: {}. Using Groovy script from file: {}", executionMode, executor);
            } else if (!executor.endsWith(".java")) {
                executor += ".java";
                log.warn("Execution mode: {}. Using internal Java executor: {}", executionMode, executor);
            }
        } else {
            String executorName = Utils.getRegexGroup(executor, "\\bclass\\s+(\\w+)\\s+implements\\s+IExecutor\\s+\\{", 1);
            log.warn("Execution mode: {}. Using inline Groovy script from recipe body{}{}",
                    executionMode,
                    executorName != null ? ": " : "",
                    executorName != null ? executorName : "");
        }

        final String flowName;
        final List<TaskMap> listTaskMap;
        if (!executor.endsWith(".java")) {
            String executorName = executor;
            if (executor.contains("\n")) {
                executorName = Utils.getRegexGroup(executor, "\\bclass\\s+(\\w+)\\s+implements\\s+IExecutor\\s+\\{", 1);
            }
            log.info("Trying to parse the groovy executor: {}", executorName);
            IExecutor groovyObject = scriptService.getGroovyExecutor(executor);
            log.info("Preparing the list of tasks");
            listTaskMap = groovyObject.prepareListOfTasks(applicationContext, flowKey);

            flowName = groovyObject.getClass().getName() + "-" + flowKey;
        } else {
            log.info("Searching for an internal Java executor: {}", executor);
            String internalExecutorName = executor.replace(".java", "").replace(".class", "");
            IExecutor internalExecutor = applicationContext.getBean(internalExecutorName, IExecutor.class);
            log.info("Preparing the list of tasks");
            listTaskMap = internalExecutor.prepareListOfTasks(applicationContext, flowKey);

            flowName = internalExecutorName + "-" + flowKey;
        }

        Queue<Task> finalTasks = new ConcurrentLinkedQueue<>();
        var numberOfTasks = listTaskMap.size();
        IntStream.range(0, listTaskMap.size()).forEach(i -> {
            var it = listTaskMap.get(i);
            finalTasks.add(Task.builder()
                    .name(it.getName())
                    .status(EnumTaskStatus.NEW)
                    .startMessage("[" + (i + 1) + "/" + numberOfTasks + "] Running - " + it.getName())
                    .endMessage("[" + (i + 1) + "/" + numberOfTasks + "] Completed - " + it.getName())
                    .weight(1)
                    .runnable(it.getRunnable())
                    .build());
        });

        Flow flow = Flow.builder()
                .projectUUID(projectUUID)
                .contentHash(contentHash)
                .contextKey(contextKey)
                .name(flowName)
                .status(EnumTaskStatus.NEW)
                .startMessage("Starting the execution of " + flowName)
                .endMessage("Finished SUCCESSFULLY the execution of " + flowName)
                .tasks(finalTasks)
                .totalWeight(finalTasks.stream().mapToInt(Task::getWeight).sum())
                .projectContext(projectContext)
                .build();

        log.info("Starting the tasks execution...");
        flow.executeAsync();

        Flow.PROJECT_FLOW.put(flowKey, flow);

        return flow;
    }

    public Flow code2(UUID projectUUID, String contextKey, String recipeContent, Map<String, Object> recipe, Map<String, Object> configs, Map<String, String> files) throws Exception {
        int contentHash = Objects.hash(recipeContent, configs, files);

        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        if (Flow.PROJECT_FLOW.get(flowKey) instanceof Flow currentFlow) {
            switch (currentFlow.getStatus()) {
                case EnumTaskStatus.RUNNING:
                    return currentFlow;
                case EnumTaskStatus.INTERRUPTED:
                    currentFlow.executeAsync();
                    return currentFlow;
                case EnumTaskStatus.ERROR:
                    if (contentHash == currentFlow.getContentHash()) {
                        if (currentFlow.getCurrentTask() instanceof Task currentTask && currentTask.getStatus() != EnumTaskStatus.COMPLETE) {
                            currentTask.setStatus(EnumTaskStatus.NEW);
                        }

                        currentFlow.executeAsync();
                        return currentFlow;
                    }

                    log.error("Something change since the last call, so the Flow with '{}' flowKey will be recreated!", flowKey);
                    Flow.PROJECT_FLOW.remove(flowKey);
                    break;
                default:
                    log.error("None Flow was found with '{}' flowKey.", flowKey);
                    Flow.PROJECT_FLOW.remove(flowKey);
                    break;
            }
        }

        var concurrentRecipe = convertToConcurrent(recipe);

        var rootFolder = FileUtils.absolutePathJoin(FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH, flowKey);
        var isFresh = Boolean.parseBoolean(String.valueOf(Utils.anyCollectionGet(concurrentRecipe, "config.fresh")));
        if (isFresh) {
            contextService.startNewProjectContextKey(projectUUID, contextKey);
            FileUtils.recreateEmptyIfExist(rootFolder);
        }

        if (contextService.getProjectContext() == null) {
            var projectContextBackupPath = FileUtils.absolutePathJoin(rootFolder, "meta", "projectContext");
            if (FileUtils.isFileExists(projectContextBackupPath) && !isFresh) {
                String projectContextBackupString = FileUtil.readAsString(new File(projectContextBackupPath.toString()));
                var projectContext = (ConcurrentLinkedHashMap<String, Object>) Utils.convertToConcurrent(JsonUtils.readAsMap(projectContextBackupString));
                contextService.loadProjectContext(flowKey, projectContext);
            } else {
                contextService.startNewProjectContextKey(projectUUID, contextKey);
            }
        }
        var projectContext = contextService.getProjectContext();
        projectContext.put("recipe", concurrentRecipe);

        var apiContext = Utils.convertToConcurrent(Map.of(
                "configs", configs,
                "files", files
        ));
        projectContext.put("$api", apiContext);

        List<AgentDto> agents = new ConcurrentLinkedList<>();
        if (recipe.get("config") instanceof Map<?, ?> configMap) {
            if (configMap.get("agents") instanceof List<?> agentsList) {
                var agentsListString = JsonUtils.writeAsJsonString(agentsList, true);
                List<AgentDto> listOfAgentDto = JsonUtils.readAsListOf(agentsListString, AgentDto.class);
                agents.addAll(listOfAgentDto);
            }
        }
        projectContext.put("agents", agents);

        Map<String, Grammar> projectContextGrammars = new ConcurrentLinkedHashMap<>();
        Map<String, GrammarDto> recipeGrammars = recipe.get("grammars") instanceof Map<?, ?> scriptsMap
                ? JsonUtils.readAsMap(JsonUtils.writeAsJsonString(scriptsMap, true), GrammarDto.class)
                : new ConcurrentLinkedHashMap<>();
        recipeGrammars.forEach((grammarKey, grammarDto) -> {
            try {
                String entryRule = scriptService.evalIfSpEL(grammarDto.getEntryRule());

                String lexerBody = scriptService.evalIfSpEL(grammarDto.getLexer().getBody());
                if (Utils.isDebugMode() && grammarDto.getLexer().getReference() instanceof String lexerReference) {
                    var lexerPath = FileUtils.pathJoin("src/main/resources/antlr4/", lexerReference);
                    log.warn("[GRAMMARS] File '{}' will be used in place of the Lexer body!", lexerPath);
                    lexerBody = Files.readString(lexerPath);
                }

                String parserBody = scriptService.evalIfSpEL(grammarDto.getParser().getBody());
                if (Utils.isDebugMode() && grammarDto.getParser().getReference() instanceof String parserReference) {
                    var parserPath = FileUtils.pathJoin("src/main/resources/antlr4/", parserReference);
                    log.warn("[GRAMMARS] File '{}' will be used in place of the Grammar body!", parserPath);
                    parserBody = Files.readString(parserPath);
                }

                Map<String, String> extraG4s = new ConcurrentLinkedHashMap<>();
                grammarDto.getDependencies().forEach((extraKey, extraGrammarScriptDto) -> {
                    String extraEvaluatedKey = scriptService.evalIfSpEL(extraKey);
                    String extraBody = scriptService.evalIfSpEL(extraGrammarScriptDto.getBody());
                    if (Utils.isDebugMode() && extraGrammarScriptDto.getReference() instanceof String extraReference) {
                        var extraPath = FileUtils.pathJoin("src/main/resources/antlr4/", extraReference);
                        log.warn("[GRAMMARS-DEPENDENCIES] File '{}' will be used in place of the Extra body!", extraPath);
                        try {
                            extraBody = Files.readString(extraPath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    extraG4s.put(extraEvaluatedKey, extraBody);
                });

                projectContextGrammars.put(grammarKey, ParserUtils.prepareGrammar(grammarKey, grammarDto.getEngine(), lexerBody, parserBody, entryRule, extraG4s));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        projectContext.put("grammars", projectContextGrammars);

        projectContext.put("rootFolder", rootFolder.toString());

        log.info("flowKey        = {}", flowKey);
        log.info("USER_TEMP_PATH = {}", FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH);
        var progressCustomDataExpression = String.valueOf(Utils.nvl(Utils.anyCollectionGet(concurrentRecipe, "config.progressCustomData"), "")).trim();
        projectContext.put("$progressCustomDataExpression", progressCustomDataExpression);

        String executor = JsonPath.read(concurrentRecipe, "$.executor");
        if (!executor.contains("\n")) {
            if (executor.endsWith(".groovy")) {
                if (!Utils.isDebugMode()) {
                    throw new IllegalArgumentException("In production, the whole executor script should be included on the recipe, not only the reference!");
                }
                executor = FileUtils.absolutePathJoin(EXECUTORS_PATH, executor).toString();
            } else if (!executor.endsWith(".java")) {
                executor += ".java";
            }
        }

        final Flow flow;
        if (!executor.endsWith(".java")) {
            String executorName = executor;
            if (executor.contains("\n")) {
                executorName = Utils.getRegexGroup(executor, "\\bclass\\s+(\\w+)\\s+implements\\s+IExecutor\\s+\\{", 1);
            }
            log.info("Trying to parse the groovy executor: {}", executorName);
            IExecutor groovyObject = scriptService.getGroovyExecutor(executor);
            log.info("Preparing the flow");
            flow = groovyObject.prepareFlow(applicationContext, projectUUID, contentHash, contextKey);
        } else {
            log.info("Searching for an internal Java executor: {}", executor);
            String internalExecutorName = executor.replace(".java", "").replace(".class", "");
            IExecutor internalExecutor = applicationContext.getBean(internalExecutorName, IExecutor.class);
            log.info("Preparing the flow");
            flow = internalExecutor.prepareFlow(applicationContext, projectUUID, contentHash, contextKey);
        }

        Flow.PROJECT_FLOW.put(flowKey, flow);

        log.info("Starting the tasks execution...");
        flow.executeAsync();

        return flow;
    }
}
