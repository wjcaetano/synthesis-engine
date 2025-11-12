package com.capco.brsp.synthesisengine.controller;

import ch.qos.logback.core.util.StringUtil;
import com.capco.brsp.synthesisengine.configuration.UtilsConfig;
import com.capco.brsp.synthesisengine.dto.*;
import com.capco.brsp.synthesisengine.flow.EnumTaskStatus;
import com.capco.brsp.synthesisengine.flow.Flow;
import com.capco.brsp.synthesisengine.flow.Task;
import com.capco.brsp.synthesisengine.service.*;
import com.capco.brsp.synthesisengine.tools.ToolsService;
import com.capco.brsp.synthesisengine.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.capco.brsp.synthesisengine.utils.FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;

@Slf4j
@RestController
@RequestMapping("/api/v1/{projectUUID}")
@RequiredArgsConstructor
public class SynthesisEngineController {
    @Qualifier("synthesisEngineService")
    private final SynthesisEngineService synthesisEngineService;
    private final ContextService contextService;

    private final ToolsService toolsService;
    private final ScriptService scriptService;
    private final ScriptService2 scriptService2;
    private final PlantUMLService plantUMLService;
    private final LLMSpringService llmSpringService;
    private final LLMEmbeddingSpringService llmEmbeddingSpringService;
    private final UtilsConfig utilsConfig;

    public static LocalDateTime convertDateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }

        Instant instant = date.toInstant();

        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    @PostMapping("chain")
    public ResponseEntity<Object> chain(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody ChainRequestDto chainRequestDto) throws JsonProcessingException, InvocationTargetException, IllegalAccessException {
        contextService.setFlowKey(projectUUID, contextKey);

        Map<String, Object> context = new ConcurrentLinkedHashMap<>();
        context.put("projectUUID", projectUUID);

        var chain = chainRequestDto.getChain();
        Object chainResults = toolsService.chainExecute(context, chain);
        var resultSelection = chainRequestDto.getResultSelection();
        if (scriptService.isValidSpEL(resultSelection)) {
            chainResults = scriptService.evalSpEL(context, resultSelection);
        } else if (!StringUtil.isNullOrEmpty(resultSelection)) {
            chainResults = Utils.anyCollectionGet(chainResults, resultSelection);
        }

        return ResponseEntity.ok(chainResults);
    }

    @PostMapping("/saveFile")
    public ResponseEntity<ResponseSaveFileDto> saveFile(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody RequestSaveFileDto requestSaveFileDto) {
        var flowKey = contextService.setFlowKey(projectUUID, contextKey);

        try {
            var relativeFilePath = Paths.get(requestSaveFileDto.getPath()).toString();
            var fullFilePath = FileUtils.absolutePathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, flowKey, relativeFilePath);
            File file = new File(fullFilePath.toString());
            if (!file.exists()) {
                ResponseEntity.notFound();
            }

            ConcurrentLinkedHashMap<String, Object> projectContext = contextService.getProjectContext();
            if (projectContext != null && projectContext.get("files_metadata") instanceof Map<?, ?> allMetadataMap && allMetadataMap.get(relativeFilePath) instanceof Map<?, ?> fileMetadataMap) {
                if (fileMetadataMap.get("history") instanceof List<?> historyGeneric) {
                    @SuppressWarnings("unchecked")
                    var history = (List<TransformDto>) historyGeneric;
                    var oldContent = Files.readString(fullFilePath);
                    var transformDto = TransformDto.builder()
                            .name("User")
                            .content(oldContent)
                            .build();

                    ResponseSaveFileDto responseSaveFileDto = ResponseSaveFileDto.builder()
                            .error(false)
                            .message("Successfully updated the content of the file!")
                            .build();

                    Files.writeString(fullFilePath, requestSaveFileDto.getContent());
                    history.add(transformDto);

                    return ResponseEntity.ok(responseSaveFileDto);
                } else {
                    ResponseSaveFileDto responseSaveFileDto = ResponseSaveFileDto.builder()
                            .error(true)
                            .errorMessage("Failed to retrieve history of the specified file!")
                            .build();
                    return ResponseEntity.unprocessableEntity().body(responseSaveFileDto);
                }
            } else {
                ResponseSaveFileDto responseSaveFileDto = ResponseSaveFileDto.builder()
                        .error(true)
                        .errorMessage("Failed to retrieve metadata of the specified file!")
                        .build();
                return ResponseEntity.unprocessableEntity().body(responseSaveFileDto);
            }
        } catch (Exception ex) {
            ResponseSaveFileDto responseSaveFileDto = ResponseSaveFileDto.builder()
                    .error(true)
                    .errorMessage(ex.getMessage())
                    .errorStack(Utils.getStackTraceAsString(ex))
                    .message(null)
                    .build();

            return ResponseEntity.internalServerError().body(responseSaveFileDto);
        }
    }

    @PostMapping("/startJob")
    public ResponseEntity<ProgressDto> startJob(
            @PathVariable("projectUUID") UUID projectUUID,
            @RequestParam(value = "contextKey", defaultValue = "default") String contextKey,
            @RequestBody RequestDto requestDto) throws Exception {
        var finalRecipeContent = requestDto.getRecipeContent();
        if (scriptService2.isValidSpEL(finalRecipeContent)) {
            var tempContext = new ConcurrentLinkedHashMap<String, Object>();
            Utils.anyCollectionSet(tempContext, "$api.files", requestDto.getFiles());
            finalRecipeContent = (String) scriptService2.evalSpEL(tempContext, finalRecipeContent);
        }

        if (Arrays.stream(Utils.class.getDeclaredMethods()).anyMatch(it -> it.getName().length() == 1)) {
            finalRecipeContent = Utils.translate(finalRecipeContent, requestDto.getObfuscationMap());
        }

        var recipe = synthesisEngineService.readRecipe(finalRecipeContent);

        Flow flow;
        if (recipe.get("executor") instanceof String executorString && executorString.contains("ProjectModelExecutor3")) {
            flow = synthesisEngineService.code2(projectUUID, contextKey, finalRecipeContent, recipe, requestDto.getConfigs(), requestDto.getFiles());
        } else {
            flow = synthesisEngineService.code(projectUUID, contextKey, finalRecipeContent, recipe, requestDto.getConfigs(), requestDto.getFiles());
        }

        // TODO: Review!
        contextService.getProjectContext().put("llmConfig", requestDto.getConfigs());

        var progressDto = getProgressDto(flow, utilsConfig.includeFileContentOnProgressResponse);

        return ResponseEntity.ok(progressDto);
    }

    @PostMapping("/redoTask")
    public ResponseEntity<ProgressDto> redoTask(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody Map<String, Object> item) throws Exception {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        Flow flow = Flow.PROJECT_FLOW.get(flowKey);

        if (flow == null) {
            return ResponseEntity.notFound().build();
        }

        var progressDto = getProgressDto(flow, utilsConfig.includeFileContentOnProgressResponse);

        if (flow.getStatus().equals(EnumTaskStatus.RUNNING)) {
            return ResponseEntity.unprocessableEntity().body(progressDto);
        }

        var itemPath = String.valueOf(item.get("path"));
        var itemValue = String.valueOf(item.get("value"));

        AtomicReference<ResponseEntity<ProgressDto>> responseEntity = new AtomicReference<>();
        progressDto.getFiles().stream().filter(it -> it.getPath().equalsIgnoreCase(itemPath)).findFirst().ifPresentOrElse(it -> {
            var task = Task.builder()
                    .name("Redo")
                    .status(EnumTaskStatus.NEW)
                    .startMessage("Running - Redo for: " + it.getPath())
                    .endMessage("Completed - Redo for: " + it.getPath())
                    .weight(1)
                    .runnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                contextService.setFlowKey(flowKey);
                                var path = Paths.get(it.getPath());
                                var result = String.valueOf(scriptService.autoEval(itemValue, it.getHistory()));
                                var projectContext = contextService.getProjectContext();
                                var fullFilePath = FileUtils.absolutePathJoin(projectContext.get("rootFolder"), path);
                                FileUtils.writeFile(fullFilePath, result, false);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    .build();

            flow.getTasks().add(task);

            flow.executeAsync();

            try {
                ProgressDto newProgressDto = getProgressDto(flow, utilsConfig.includeFileContentOnProgressResponse);
                responseEntity.set(ResponseEntity.ok(newProgressDto));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, () -> {
            responseEntity.set(ResponseEntity.notFound().build());
        });

        return responseEntity.get();
    }

    private ResponseEntity<ProgressDto> prepareProgressDto(String flowKey, boolean includeContent) throws IOException {
        Flow flow = Flow.PROJECT_FLOW.get(flowKey);

        if (flow == null) {
            return ResponseEntity.notFound().build();
        }

        var progressDto = getProgressDto(flow, includeContent);

        return ResponseEntity.ok(progressDto);
    }

    @PostMapping("/responseAnswers")
    public ResponseEntity<ProgressDto> responseAnswers(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody(required = false) Map<String, Object> askAnswers) throws IOException {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        var progressDto = prepareProgressDto(flowKey, utilsConfig.includeFileContentOnProgressResponse);

        if (askAnswers != null && !askAnswers.isEmpty() && progressDto.getStatusCode() != HttpStatusCode.valueOf(404)) {
            var projectContext = contextService.getProjectContext();
            askAnswers.forEach((k, v) -> {
                var askQueue = ((List<Map<String, Object>>) projectContext.get("askQueue"));
                askQueue.removeIf(it -> k.equals(it.get("key")));
                Utils.anyCollectionSet(projectContext, k, v);
            });
        }

        return progressDto;
    }

    @PostMapping("/testPrompt")
    public ResponseEntity<String> springAnswer(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.get("prompt");
        Map<String, Object> configMap = (Map<String, Object>) body.get("agent");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("Prompt cannot be null or empty");
        }

        AgentDto config = new AgentDto();

        if (configMap != null) {
            config.setProvider((String) configMap.get("provider"));
            config.setModel((String) configMap.get("model"));
            config.setTemperature((Double) configMap.get("temperature"));
            config.setDeploymentName((String) configMap.get("deploymentName"));
        } else {
            // Default configuration if none provided
            config.setProvider("azure");
            config.setModel("gpt-4o");
            config.setTemperature(0.7);
            config.setDeploymentName("Chatbot");
        }
        String response;
        try {
            response = llmSpringService.prompt(prompt, config);
        } catch (Exception e) {
            log.error("Error processing prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing prompt: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/promptWithTools")
    public ResponseEntity<String> springAnswerWithTools(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.get("prompt");
        Map<String, Object> configMap = (Map<String, Object>) body.get("agent");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("Prompt cannot be null or empty");
        }

        AgentDto config = new AgentDto();

        if (configMap != null) {
            config.setProvider((String) configMap.get("provider"));
            config.setModel((String) configMap.get("model"));
            config.setTemperature((Double) configMap.get("temperature"));
            config.setDeploymentName((String) configMap.get("deploymentName"));
            config.setTools((List<String>) configMap.get("tools"));
        } else {
            config.setProvider("azure");
            config.setModel("gpt-4o");
            config.setTemperature(0.7);
            config.setDeploymentName("Chatbot");
        }

        String response;
        try {
            response = llmSpringService.prompt(prompt, config);
        } catch (Exception e) {
            log.error("Error processing prompt with tools: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing prompt with tools: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/promptWithFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> promptWithFile(
            @RequestPart("prompt") String prompt,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "agent", required = false) String agentConfig) {
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("Prompt cannot be null or empty");
        }

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("File cannot be null or empty");
        }

        AgentDto config = new AgentDto();
        try {
            if (agentConfig != null && !agentConfig.isBlank()) {
                ObjectMapper objectMapper = new ObjectMapper();
                config = objectMapper.readValue(agentConfig, AgentDto.class);
            } else {
                config.setProvider("azure");
                config.setModel("gpt-4o");
                config.setTemperature(0.7);
                config.setDeploymentName("Chatbot");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error parsing agent configuration: " + e.getMessage());
        }

        String response;
        try {
            response = llmSpringService.promptWithFile(prompt, files, config);
        } catch (Exception e) {
            log.error("Error processing prompt with file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing prompt with file: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/promptEmbedding")
    public ResponseEntity<Object> promptEmbedding(
            @RequestPart("prompt") String prompt,
            @RequestPart(value = "config", required = false) String embeddingConfig) {

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("Prompt cannot be null or empty");
        }

        if (embeddingConfig == null) {
            return ResponseEntity.badRequest().body("Config cannot be null");
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            AgentEmbConfigDto configDto = objectMapper.readValue(embeddingConfig, AgentEmbConfigDto.class);
            float[][] response = llmEmbeddingSpringService.promptEmbeddingAsArray(prompt, configDto);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing embedding prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing embedding prompt: " + e.getMessage());
        }
    }

    @PostMapping("/fileSimilarity")
    public ResponseEntity<Object> fileSimilarity(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("config") String embeddingConfig) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("Files cannot be null or empty");
        }
        if (embeddingConfig == null) {
            return ResponseEntity.badRequest().body("Config cannot be null");
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            AgentEmbConfigDto configDto = objectMapper.readValue(embeddingConfig, AgentEmbConfigDto.class);

            List<String> fileNames = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            for (MultipartFile file : files) {
                fileNames.add(file.getOriginalFilename());
                contents.add(new String(file.getBytes()));
            }

            Map<String, Object> result = llmEmbeddingSpringService.compareFilesSimilarity(fileNames, contents, configDto);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error processing file similarity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file similarity: " + e.getMessage());
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<ProgressDto> getJobExecution(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestParam(value = "includeContext", defaultValue = "false") boolean includeContext) throws IOException {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        var progressDto = prepareProgressDto(flowKey, utilsConfig.includeFileContentOnProgressResponse || includeContext);
        if (!utilsConfig.includeFileContentOnProgressResponse && !includeContext) {
            var projectContext = JsonUtils.readAsMap(progressDto.getBody().getProjectContext());
            var reducedProjectContext = new ConcurrentLinkedHashMap<>();
            reducedProjectContext.put("askQueue", projectContext.remove("askQueue"));
            var reducedProjectContextString = JsonUtils.writeAsJsonString(reducedProjectContext, true);
            progressDto.getBody().setProjectContext(reducedProjectContextString);
        }

        return progressDto;
    }

    @GetMapping("/optionsFromRecipe")
    public ResponseEntity<List<?>> getOptionsFromRecipe(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "recipeContent") String recipeContent) {
        return postOptionsFromRecipe(projectUUID, RequestDto.builder().recipeContent(recipeContent).files(new ConcurrentLinkedHashMap<>()).build());
    }

    @PostMapping("/optionsFromRecipe")
    public ResponseEntity<List<?>> postOptionsFromRecipe(@PathVariable("projectUUID") UUID projectUUID, @RequestBody RequestDto requestDto) {
        var finalRecipeContent = requestDto.getRecipeContent();
        if (scriptService2.isValidSpEL(finalRecipeContent)) {
            var tempContext = new ConcurrentLinkedHashMap<String, Object>();
            Utils.anyCollectionSet(tempContext, "$api.files", requestDto.getFiles());
            finalRecipeContent = (String) scriptService2.evalSpEL(tempContext, finalRecipeContent);
        }

        if (Arrays.stream(Utils.class.getDeclaredMethods()).anyMatch(it -> it.getName().length() == 1)) {
            finalRecipeContent = Utils.translate(finalRecipeContent, new ConcurrentLinkedHashMap<>());
        }
        try {
            Map<String, Object> recipe = synthesisEngineService.readRecipe(finalRecipeContent);
            Map<String, Object> config = (Map<String, Object>) recipe.get("config");
            if (config != null && config.containsKey("options")) {
                List<?> options = (List<?>) config.get("options");
                return ResponseEntity.ok(options);
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (Exception ignored) {
            return ResponseEntity.badRequest().build();
        }
    }


    private ProgressDto getProgressDto(Flow flow, boolean includeContent, String... responseOptions) throws IOException {
        var projectUUID = flow.getProjectUUID();
        var flowKey = flow.getFlowKey();

        contextService.setFlowKey(flowKey);

        var path = FileUtils.absolutePathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, flowKey).toString();
        List<FileDto> files = FileUtils.crawlDirectory(path);

        ConcurrentLinkedHashMap<String, Object> projectContext = contextService.getProjectContext();

        Map<String, Object> projectContextSnapshot = Objects.requireNonNull(projectContext).createSnapshot();

        Object customData = null;
        if (projectContextSnapshot != null) {
            var progressCustomDataExpression = String.valueOf(projectContextSnapshot.get("$progressCustomDataExpression"));

            if (scriptService.isValidSpEL(progressCustomDataExpression)) {
                customData = scriptService.evalSpEL(projectContextSnapshot, progressCustomDataExpression);
            }

            if (projectContextSnapshot.get("files_metadata") instanceof Map<?, ?> map) {
                map.forEach((key, value) -> {
                    if (value instanceof Map<?, ?> metadata) {
                        metadata.remove("meta");
                        if (metadata.get("history") instanceof List<?> historyGeneric) {
                            @SuppressWarnings("unchecked")
                            var history = (List<TransformDto>) historyGeneric;
                            files.stream().filter(it -> FileUtils.equalsPaths(it.getPath(), key)).findFirst().ifPresent(fileDto -> {
                                List<TransformDto> copiedHistory = history == null
                                        ? null
                                        : history.stream()
                                        .map(TransformDto::deepClone)
                                        .toList();

                                fileDto.setHistory(copiedHistory);
                            });
                        } else {
                            throw new IllegalStateException("History content metadata from the file '" + key + "' is not a list!");
                        }
                    }
                });
            }

            for (FileDto file : files) {
                file.setPath(file.getPath().replace("\\", "/"));

                if (!includeContent) {
                    file.setContent(null);
                    if (file.getHistory() != null) {
                        for (var history : file.getHistory()) {
                            history.setContent(null);
                        }
                    }
                }
            }
        }

        var projectContextString = JsonUtils.writeAsJsonStringCircular(projectContextSnapshot, true, false);
        if (!includeContent) {
            var finalProjectContext = JsonUtils.readAsMap(projectContextString);
            if (finalProjectContext.get("files_metadata") instanceof Map<?, ?> map) {
                for (var fileEntry : map.entrySet()) {
                    if (fileEntry.getValue() instanceof Map<?, ?> fileMap && fileMap.get("history") instanceof List<?> list) {
                        for (var historyItem : list) {
                            ((Map<String, Object>) historyItem).put("content", null);
                        }
                    }
                }
            }

            projectContextString = JsonUtils.writeAsJsonString(finalProjectContext, true);
        }

        JobExecutionDto flowDto = getFlowDto(flow);

        var listOfOptions = Arrays.stream(responseOptions).toList();
        if (listOfOptions.isEmpty()) {
            listOfOptions = Arrays.asList("customData", "files", "projectContext");
        }

        return ProgressDto.builder()
                .projectUUID(projectUUID)
                .version((long) 0)
                .customData(listOfOptions.contains("customData") ? customData : null)
                .files(listOfOptions.contains("files") ? files : null)
                .projectContext(listOfOptions.contains("projectContext") ? projectContextString : "{}")
                .job(flowDto)
                .build();
    }

    public JobExecutionDto getFlowDto(Flow flow) {
        List<StepInfoDto> steps = new ConcurrentLinkedList<>();

        List<Task> allTasks = new ConcurrentLinkedList<>(flow.getPolledTasks());
        allTasks.addAll(flow.getTasks());
        allTasks.forEach(it -> {
            StepInfoDto stepInfo = new StepInfoDto();
            LocalDateTime startTime = convertDateToLocalDateTime(it.getStartedAt());
            LocalDateTime endTime = convertDateToLocalDateTime(it.getFinishedAt());

            stepInfo.setStartTime(startTime);
            stepInfo.setEndTime(endTime);

            long totalTime = Duration.between(stepInfo.getStartTime() != null ? stepInfo.getStartTime() : LocalDateTime.now(), stepInfo.getEndTime() != null ? stepInfo.getEndTime() : LocalDateTime.now()).toMillis();
            stepInfo.setTotalTime(totalTime);

            stepInfo = new StepInfoDto();
            stepInfo.setName(it.getName());
            stepInfo.setStatus(it.getStatus().name());
            stepInfo.setNumberOfAttempts(0);
            stepInfo.setStartTime(startTime);
            stepInfo.setEndTime(endTime);
            stepInfo.setTotalTime(0);

            steps.add(stepInfo);
        });


        int totalSteps = flow.getTotalTasks();
        var currentStep = flow.getCurrentTask();

        LocalDateTime startTime = convertDateToLocalDateTime(flow.getStartedAt());
        LocalDateTime endTime = convertDateToLocalDateTime(flow.getFinishedAt());
        long totalTime = Duration.between(startTime != null ? startTime : LocalDateTime.now(), endTime != null ? endTime : LocalDateTime.now()).toMillis();

        return JobExecutionDto.builder()
                .jobName(flow.getName())
                .status(flow.getStatus().name())
                .steps(steps)
                .numberOfAttempts(1)
                .completedSteps(flow.getTotalTasksDone())
                .totalSteps(steps.size())
                .startTime(startTime)
                .endTime(endTime)
                .totalTime(totalTime)
                .completionPercentage(totalSteps > 0 ? flow.getTotalTasksDone() * 100 / totalSteps : 0)
                .currentStepName(currentStep == null ? null : currentStep.getName())
                .currentStepStatus(currentStep == null ? flow.getStatus().name() : currentStep.getStatus().name())
                .rotateMessages(Utils.safeGet(() -> flow.getCurrentTask().getRotateMessages(), new ConcurrentLinkedList<>()))
                .build();
    }

    @PostMapping("/restartJob")
    public ResponseEntity<String> restartJob(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody RequestDto requestDto) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/removeJob")
    public ResponseEntity<Boolean> removeJob(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody RequestDto requestDto) {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        var removed = Flow.PROJECT_FLOW.remove(flowKey);

        log.info("Flow removed: {}", flowKey);

        return ResponseEntity.ok(true);
    }

    @PostMapping("/stopJob")
    public ResponseEntity<Boolean> stopJob(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody RequestDto requestDto) {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        var flow = Flow.PROJECT_FLOW.get(flowKey);

        if (flow == null) {
            return ResponseEntity.notFound().build();
        }

        flow.stop();

        return ResponseEntity.ok(true);
    }

    @PostMapping("/execute")
    public ResponseEntity<ProgressDto> execute(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody ExecuteRequestDto executeRequestDto) throws Exception {
        var flowKey = synthesisEngineService.execute(projectUUID, contextKey, executeRequestDto.getType(), executeRequestDto.getPath(), executeRequestDto.getExpression());
        return prepareProgressDto(flowKey, utilsConfig.includeFileContentOnProgressResponse);
    }

    @PostMapping("/removeCache")
    public ResponseEntity<String> execute(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "code-factory") String contextKey, @RequestBody Map<String, Object> bodyMap) throws Exception {
        List<String> cacheHashes = new ConcurrentLinkedList<>();

        var flowKey = Utils.combinedKey(projectUUID, contextKey);
        Flow flow = Flow.PROJECT_FLOW.get(flowKey);
        contextService.setFlowKey(flowKey);

        if (bodyMap.get("caches") instanceof List<?> caches) {
            cacheHashes.addAll((List<String>) caches);
        }

        if (bodyMap.get("uuid") instanceof String uuidString) {
            var uuid = UUID.fromString(uuidString);

            var progress = getProgressDto(flow, utilsConfig.includeFileContentOnProgressResponse);
            progress.getFiles().stream().filter(it -> it.getHistory() != null).forEach(it -> {
                it.getHistory().forEach(it2 -> {
                    if (it2.getUuid().equals(uuid)) {
                        cacheHashes.addAll(it2.getCaches());
                    }
                });
            });
        }

        if (cacheHashes.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid UUID sent in attempt to clear some transform cache!");
        }

        cacheHashes.forEach(it -> {
            try {
                scriptService.removeCache(flowKey, it);
            } catch (Exception ignored) {
            }

            try {
                scriptService2.removeCache(flowKey, it);
            } catch (Exception ignored) {
            }
        });

        return ResponseEntity.ok("Caches removidos!");
    }

    @PostMapping("/content")
    public ResponseEntity<ContentDto> execute(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestBody ContentDto contentDto) throws Exception {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);
        contentDto.setFlowKey(flowKey);

        List<String> errors = new ArrayList<>();

        if (Utils.isEmpty(contentDto.getPath())) {
            errors.add("Field 'path' is required!");
        }

        String pattern = "^\\[(\\d+)](\\[(\\d+)])*$";

        String historyIndex = null;
        if (contentDto.getHistoryIndex() instanceof String historyIndexString) {
            historyIndex = historyIndexString;
            if (!historyIndex.isEmpty() && !historyIndex.matches(pattern)) {
                errors.add("Field 'historyIndex' must be empty or match the pattern: " + pattern + " (e.g., [0], [1][2], etc.)");
            }
        }

        if (!errors.isEmpty()) {
            contentDto.setError(true);
            contentDto.setErrorMessage(String.join("\n", errors));
            return ResponseEntity.badRequest().body(contentDto);
        }

        Flow flow = Flow.PROJECT_FLOW.get(flowKey);
        var progressDto = getProgressDto(flow, true);
        var fileDto = progressDto.getFiles().stream().filter(it -> it.getPath().equals(contentDto.getPath())).findFirst().orElse(null);
        if (fileDto == null) {
            contentDto.setError(true);
            contentDto.setErrorMessage("File not found: " + contentDto.getPath());
            return ResponseEntity.status(404).body(contentDto);
        }

        if (historyIndex == null) {
            contentDto.setContent(fileDto.getContent());
        } else {
            var fileHistoryPathExpression = "${#file" + historyIndex.replace("]", "]?.").replace("[", "['history']?.[") + "['content']}";
            try {
                var fileHistoryContent = (String) scriptService.evalSpEL(Map.of("file", fileDto), fileHistoryPathExpression);

                if (fileHistoryContent == null) {
                    contentDto.setError(true);
                    contentDto.setErrorMessage("No history available for the file: " + contentDto.getPath() + " at index: " + historyIndex);
                    return ResponseEntity.status(404).body(contentDto);
                }

                contentDto.setContent(fileHistoryContent);
            } catch (SpelEvaluationException ex) {
                contentDto.setError(true);
                contentDto.setErrorMessage(ex.getMessage());
                return ResponseEntity.status(404).body(contentDto);
            }
        }

        return ResponseEntity.ok(contentDto);
    }

    @GetMapping("/logs")
    public ResponseEntity<Resource> logs(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey) throws Exception {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        String logFileName = flowKey + "-logs.log";
        String logContent = "Not implemented yet!";
        ByteArrayResource resource = new ByteArrayResource(logContent.getBytes());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + logFileName + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @GetMapping(path = "/download")
    public ResponseEntity<Resource> download(@PathVariable("projectUUID") UUID projectUUID, @RequestParam(value = "contextKey", defaultValue = "default") String contextKey, @RequestHeader("Content-Type") String contentType) throws FileNotFoundException {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);

        String contentPath = FileUtils.pathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, flowKey).toString();

        String fileName = flowKey + ".zip";
        String filePath = FileUtils.pathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, fileName).toString();

        FileUtils.zipFile(contentPath, filePath);

        File file = new File(filePath);
        InputStreamResource inputStream = new InputStreamResource(new FileInputStream(file));

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(inputStream);
    }

    @PostMapping(path = "/plantuml")
    public ResponseEntity<?> plantuml(@RequestHeader(value = "X-Return-Format", required = false, defaultValue = "binary") String format, @RequestBody String plantUmlScript) throws IOException {
        if ("base64".equalsIgnoreCase(format)) {
            String base64 = plantUMLService.writeToBase64(plantUmlScript);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(base64);
        } else {
            byte[] imgData = plantUMLService.writeToBytes(plantUmlScript);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imgData.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"image.png\"");

            return new ResponseEntity<>(imgData, headers, HttpStatus.OK);
        }
    }
}
