package com.capco.brsp.synthesisengine.executors;

import com.capco.brsp.synthesisengine.dto.Script;
import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.flow.EnumTaskStatus;
import com.capco.brsp.synthesisengine.flow.Flow;
import com.capco.brsp.synthesisengine.flow.Task;
import com.capco.brsp.synthesisengine.service.*;
import com.capco.brsp.synthesisengine.utils.*;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service("ProjectModelExecutor3")
class ProjectModelExecutor3 implements IExecutor {
    ApplicationContext applicationContext = null;
    SuperService2 superService = null;
    ScriptService2 scriptService = null;
    ContextService contextService = null;
    SuperUtils superUtils = SuperUtils.getInstance();

    Logger log = LoggerFactory.getLogger(ProjectModelExecutor3.class);

    @Override
    public Flow prepareFlow(ApplicationContext applicationContext, UUID projectUUID, Integer contentHash, String contextKey) {
        log.info("Starting the executor: ProjectModelExecutor3");

        //
        // Beans
        this.applicationContext = applicationContext;
        this.superService = applicationContext.getBean(SuperService2.class);
        this.scriptService = applicationContext.getBean(ScriptService2.class);
        this.contextService = applicationContext.getBean(ContextService.class);

        var flowKey = Utils.combinedKey(projectUUID, contextKey);
        contextService.setFlowKey(flowKey);

        ConcurrentLinkedHashMap<String, Object> projectContext = contextService.getProjectContext();
        Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");

        //
        // Loading custom transformAction scripts
        Map<String, String> transforms = recipe.get("transforms") != null
                ? (Map<String, String>) recipe.get("transforms")
                : new ConcurrentLinkedHashMap<>();

        for (Map.Entry<String, String> entry : transforms.entrySet()) {
            String key = entry.getKey();
            String val = (String) Utils.castValue(entry.getValue());
            try {
                scriptService.getGroovyTransform(key, val);
            } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        //
        // Loading custom executor scripts
        Map<String, Script> scripts = recipe.get("scripts") instanceof Map<?, ?> scriptsMap
                ? JsonUtils.readAsMap(JsonUtils.writeAsJsonString(scriptsMap, true), Script.class)
                : new ConcurrentLinkedHashMap<>();

        scripts.forEach((scriptName, script) -> {
            script.setName(scriptName);
            if (Script.ScriptType.GROOVY.equals(script.getType())) {
                log.info("Trying to parse the script: {}", scriptName);
                try {
                    String scriptBody = null;
                    if (Utils.isDebugMode() && script.getReference() != null) {
                        var groovyFileName = script.getReference();
                        var scriptPath = FileUtils.pathJoin("src/main/resources/executors/", groovyFileName);
                        if (!Files.exists(scriptPath)) {
                            scriptPath = FileUtils.pathJoin("src/main/resources/transforms/", groovyFileName);
                            if (!Files.exists(scriptPath)) {
                                scriptPath = FileUtils.pathJoin("src/main/resources/scripts/", groovyFileName);
                            }
                        }
                        scriptBody = Files.readString(scriptPath);
                    } else if (script.getBody() instanceof String scriptBodyString) {
                        scriptBody = scriptService.evalIfSpEL(scriptBodyString);
                    }
                    scriptService.getGroovyExecutor(scriptBody);
                } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                log.info("Successfully parsed the script: {}", scriptName);
            }
        });

        ConcurrentLinkedHashMap<String, Object> filesMetadata =
                (ConcurrentLinkedHashMap<String, Object>) Utils.convertToConcurrent(projectContext.get("files_metadata") != null
                        ? projectContext.get("files_metadata")
                        : new ConcurrentLinkedHashMap<>());

        projectContext.put("files_metadata", filesMetadata);

        var flow = Flow.builder()
                .projectUUID(projectUUID)
                .contentHash(contentHash)
                .contextKey(contextKey)
                .name("genericFlow")
                .status(EnumTaskStatus.NEW)
                .startMessage("Starting the execution of genericFlow")
                .endMessage("Finished SUCCESSFULLY the execution of genericFlow")
                .tasks(new ConcurrentLinkedList<>())
                .totalWeight(1)
                .projectContext(projectContext)
                .build();

        Map<String, Object> projectBaseModel = (Map<String, Object>) Utils.convertToConcurrent(recipe.get("projectModel"));
        Map<String, Object> expandedProjectModel = (Map<String, Object>) Utils.convertToConcurrent(expand(projectContext, projectBaseModel));

        var tasks = createListOfTasks(expandedProjectModel, flow, filesMetadata);

        flow.getTasks().addAll(tasks);

        return flow;
    }

    private List<Task> createListOfTasks(Map<String, Object> projectModel, Flow flow, Map<String, Object> filesMetadata) {
        var tasks = new ConcurrentLinkedList<Task>();

        for (Map.Entry<String, Object> fileEntry : projectModel.entrySet()) {
            String filePath = fileEntry.getKey();
            Object fileValue = fileEntry.getValue();

            Path pathOfFilePath = Paths.get(FileUtils.sanitize(filePath));
            String fileFolder = FileUtils.restore(pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : "");
            String fileName = FileUtils.restore(pathOfFilePath.getFileName().toString());
            int extensionIdx = fileName.lastIndexOf('.');
            boolean hasExtension = extensionIdx > -1;
            String extensionName = hasExtension ? fileName.substring(extensionIdx + 1) : "";
            String fileNameWithoutExtension = hasExtension ? fileName.substring(0, extensionIdx) : fileName;

            Runnable runnable = () -> {
                contextService.setFlowKey(flow.getFlowKey());
                var context = contextService.getProjectContext();

                // Files paths
                String fullFilePath = FileUtils.restore(String.valueOf(FileUtils.absolutePathJoin(context.get("rootFolder"), FileUtils.sanitize(filePath))));

                var fileMeta = new ConcurrentLinkedHashMap<String, Object>();

                // Context file/folder/project standard variables
                fileMeta.put("fullFilePath", fullFilePath);
                fileMeta.put("filePath", filePath);
                fileMeta.put("fileFolder", fileFolder);
                fileMeta.put("fileName", fileName);
                fileMeta.put("extensionName", extensionName);
                fileMeta.put("fileNameWithoutExtension", fileNameWithoutExtension);

                context.putAll(fileMeta);
                context.put("meta", fileMeta);

                // File history
                List<TransformDto> history = new ConcurrentLinkedList<>();
                context.put("fileHistory", history);
                Map<String, Object> fileMetadata = new ConcurrentLinkedHashMap<>();
                fileMetadata.put("meta", fileMeta);
                fileMetadata.put("history", history);
                filesMetadata.put(filePath, fileMetadata);

                try {
                    if (getLastMileExpression(filePath) instanceof String lastMileExpression && scriptService.isValidSpEL(lastMileExpression)) {
                        var evaluatedLastMileExpression = scriptService.evalSpEL(context, lastMileExpression);

                        if (evaluatedLastMileExpression == null) {
                            log.info("Skipping null path!");
                        } else {
                            List<String> folderNames = evaluatedLastMileExpression instanceof String evaluatedLastMileExpressionString
                                    ? new ConcurrentLinkedList<>(List.of(evaluatedLastMileExpressionString))
                                    : (List<String>) evaluatedLastMileExpression;

                            Map<String, Object> innerProjectModel = new ConcurrentLinkedHashMap<>();
                            String parentPath = filePath.replace(lastMileExpression, "");
                            for (String folderName : folderNames) {
                                innerProjectModel.put(parentPath + folderName, fileValue);
                            }

                            var innerTasks = createListOfTasks(innerProjectModel, flow, filesMetadata);
                            innerTasks.addAll(flow.getTasks());
                            flow.getTasks().clear();
                            flow.getTasks().addAll(innerTasks);
                        }
                    } else if (!(fileValue instanceof String)) {
                        Map<String, Object> innerProjectModel = new ConcurrentLinkedHashMap<>();

                        putChildrenAsLazyTasks(filePath, fileValue, innerProjectModel);

                        var innerTasks = createListOfTasks(innerProjectModel, flow, filesMetadata);
                        innerTasks.addAll(flow.getTasks());
                        flow.getTasks().clear();
                        flow.getTasks().addAll(innerTasks);
                    } else {
                        var naturalOutput = scriptService.autoEval((String) fileValue, history);
                        if (naturalOutput == null) {
                            naturalOutput = "null";
                        }

                        if (naturalOutput instanceof String naturalOutputString) {
                            if (fileName.startsWith("_") || naturalOutputString.startsWith("// Skipped/Empty File")) {
                                log.info("Skipping the file: {}", fullFilePath);
                            } else {
                                FileUtils.writeFile(Path.of(fullFilePath), naturalOutputString, false);
                            }
                        } else if (naturalOutput instanceof byte[] naturalOutputBytes) {
                            if (fileName.startsWith("_")) {
                                log.info("Skipping the file: {}", fullFilePath);
                            } else {
                                Files.write(Path.of(fullFilePath), naturalOutputBytes);
                            }
                        } else {
                            Map<String, Object> innerProjectModel = new ConcurrentLinkedHashMap<>();
                            putChildrenAsLazyTasks(filePath, naturalOutput, innerProjectModel);

                            var innerTasks = createListOfTasks(innerProjectModel, flow, filesMetadata);
                            innerTasks.addAll(flow.getTasks());
                            flow.getTasks().clear();
                            flow.getTasks().addAll(innerTasks);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    Set<String> keysToRemove = fileMeta.keySet();
                    keysToRemove.forEach(context::remove);
                    context.remove("meta");
                }
            };

            Task task = Task.builder()
                    .name("Task for path: " + filePath)
                    .status(EnumTaskStatus.NEW)
                    .startMessage("Running task for path: " + filePath)
                    .endMessage("Completed task for path: " + filePath)
                    .weight(1)
                    .runnable(runnable)
                    .build();

            tasks.add(task);
        }

        return tasks;
    }

    public String getLastMileExpression(String expression) {
        if (!expression.contains("${")) {
            return expression;
        }

        return "${" + Arrays.stream(expression.split("\\$\\{")).toList().getLast();
    }

    private void putChildrenAsLazyTasks(String basePath, Object node, Map<String, Object> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String childKey = String.valueOf(e.getKey());
                Object childVal = e.getValue();
                String joinedSanitized = (basePath == null || basePath.isEmpty()
                        ? Paths.get(FileUtils.sanitize(childKey)).toString()
                        : Paths.get(FileUtils.sanitize(basePath), FileUtils.sanitize(childKey)).toString()
                );
                String joinedRestored = FileUtils.restore(joinedSanitized);
                evaluate(childVal, joinedRestored, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    putChildrenAsLazyTasks(basePath, m, out);
                } else {
                    throw new IllegalStateException("Folder/list expansion expects Map entries; got: " + item.getClass());
                }
            }
        } else {
            throw new IllegalStateException("Unsupported node type for folder expansion: " + node.getClass());
        }
    }

    private Map<String, Object> expand(Map<String, Object> projectContext, Map<String, Object> projectModel) {
        Map<String, Object> solved = new ConcurrentLinkedHashMap<>();

        for (Map.Entry<String, Object> entry : projectModel.entrySet()) {
            String filePath = entry.getKey();
            Object fileValue = entry.getValue();

            evaluate(fileValue, filePath, solved);
        }

        return solved;
    }

    public void evaluate(Object object, String key, Map<String, Object> targetMap) {
        String rawKey = (String) Utils.castValue(key);
        if (rawKey.startsWith("$$")) {
            rawKey = rawKey.substring(1);
        }

        if (object instanceof String val) {
            if (val.startsWith("$$")) {
                val = val.substring(1);
            }

            targetMap.put(rawKey, val);
            return;
        }

        targetMap.put(rawKey, object);
    }
}