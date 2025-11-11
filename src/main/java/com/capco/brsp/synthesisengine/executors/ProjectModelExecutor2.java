package com.capco.brsp.synthesisengine.executors;

import com.capco.brsp.synthesisengine.dto.Script;
import com.capco.brsp.synthesisengine.dto.TaskMap;
import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.service.*;
import com.capco.brsp.synthesisengine.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


@Slf4j
@Service("ProjectModelExecutor2")
class ProjectModelExecutor2 implements IExecutor {
    ApplicationContext applicationContext = null;
    SuperService2 superService = null;
    ScriptService2 scriptService = null;
    SuperUtils superUtils = SuperUtils.getInstance();

    Logger log = LoggerFactory.getLogger(ProjectModelExecutor2.class);

    @Override
    public List<TaskMap> prepareListOfTasks(ApplicationContext applicationContext, String flowKey) {
        log.info("Starting the executor: ProjectModelExecutor2");

        //
        // Beans
        this.applicationContext = applicationContext;
        this.superService = applicationContext.getBean(SuperService2.class);
        this.scriptService = applicationContext.getBean(ScriptService2.class);
        var contextService = applicationContext.getBean(ContextService.class);

        ConcurrentLinkedHashMap<String, Object> projectContext = contextService.getProjectContext();
        Map<String, Object> recipe = (Map<String, Object>) projectContext.get("recipe");
        Map<String, String> executorEvents = recipe != null && recipe.get("executorEvents") != null
                ? (Map<String, String>) recipe.get("executorEvents")
                : new ConcurrentLinkedHashMap<>();
        ConcurrentLinkedHashMap<String, Object> filesMetadata =
                (ConcurrentLinkedHashMap<String, Object>) Utils.convertToConcurrent(projectContext.get("files_metadata") != null
                        ? projectContext.get("files_metadata")
                        : new ConcurrentLinkedHashMap<>());

        projectContext.put("files_metadata", filesMetadata);

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
        // Loading custom executor scripts - TODO: iterate over any levels looking for a @@@groovy transformAction
        Map<String, String> allMapTemplates = recipe.get("prompts") != null
                ? (Map<String, String>) recipe.get("prompts")
                : new ConcurrentLinkedHashMap<>();
        allMapTemplates.putAll(executorEvents);

        allMapTemplates.forEach((key, val) -> {
            if (val.contains("@@@groovy")) {
                List<String> allGroovys = Utils.getAllRegexGroup(val, "@@@groovy\\((.+)\\)", 1);

                if (Utils.isDebugMode() && !allGroovys.isEmpty()) {
                    allGroovys.forEach(groovyScriptPath -> {
                        String castedGroovyScriptPath = (String) Utils.castValue(groovyScriptPath);
                        log.info("Trying to parse the executor: " + castedGroovyScriptPath);
                        try {
                            scriptService.getGroovyExecutor("src/main/resources/executors/" + castedGroovyScriptPath);
                        } catch (IOException | NoSuchMethodException | InvocationTargetException |
                                 InstantiationException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                        log.info("Successfully parsed the executor: " + castedGroovyScriptPath);
                    });
                } else {
                    AtomicBoolean found = new AtomicBoolean(false);
                    String groovyScript = Arrays.stream(val.split(System.lineSeparator()))
                            .filter(line -> {
                                if (!found.get() && !line.stripLeading().startsWith("@@@")) {
                                    found.set(true);
                                }
                                return found.get();
                            })
                            .collect(Collectors.joining(System.lineSeparator()));

                    String executorClassName = Utils.getRegexGroup(groovyScript, "\\bclass\\s+(\\w+)\\s+implements\\s+IExecutor\\s+\\{", 1);
                    log.info("Trying to parse the executor: " + executorClassName);
                    try {
                        scriptService.getGroovyExecutor(groovyScript);
                    } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                             IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("Successfully parsed the executor: " + executorClassName);
                }
            }
        });

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

        // TODO: try to put all of it as a Task like afterAll (I doubt it will be possible! Let's see)
        processAction(executorEvents, "beforeAll", null, null);

        if (projectContext.get("projects") == null) {
            Object projectsReference = recipe.get("projectsReference");
            if (projectsReference instanceof String) {
                Object evaluatedProjectsReference = null;
                try {
                    evaluatedProjectsReference = scriptService.autoEval((String) projectsReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (evaluatedProjectsReference instanceof String) {
                    List<Map<String, Object>> projects = null;
                    try {
                        projects = (List<Map<String, Object>>) JsonUtils.readAsObject((String) evaluatedProjectsReference, null);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    projectContext.put("projects", projects);
                } else {
                    projectContext.put("projects", evaluatedProjectsReference);
                }
            } else {
                projectContext.put("projects", projectsReference);
            }
        }

        final List<Map<String, Object>> projects = new ConcurrentLinkedList<>();
        Object rawProjects = projectContext.get("projects");

        if (rawProjects instanceof Collection<?> col) {
            for (Object item : col) {
                projects.add((Map<String, Object>) item);
            }
        } else if (rawProjects instanceof Object[] arr) {
            for (Object item : arr) {
                projects.add((Map<String, Object>) item);
            }
        }

        if (projects == null) {
            throw new IllegalStateException("Not able to identify the list of projects! Current value: " + projectContext.get("projects"));
        }

        for (int i = 0; i < projects.size(); i++) {
            projects.get(i).put("index", i);
        }

        //
        // ProjectsPrepare expansion
        if (recipe.get("projectsPrepare") != null) {
            Map<String, Object> projectsPrepareBaseModel = new ConcurrentLinkedHashMap<>();
            YamlUtils.traverse(projectsPrepareBaseModel, recipe.get("projectsPrepare"), "", key -> scriptService.evalIfSpEL(key));

            Map<String, Object> projectsPrepareModel = new ConcurrentLinkedHashMap<>(projectsPrepareBaseModel);

            int projectsPrepareItemIndex = 0;
            for (Map.Entry<String, Object> projectsPrepareItem : projectsPrepareBaseModel.entrySet()) {
                Map<String, Object> expandedProjectsPrepare = expand(projectContext, projectsPrepareModel);
                projectContext.put("projectsPrepare", Utils.convertToConcurrent(expandedProjectsPrepare));
                projectsPrepareItemIndex++;
            }

            Map<String, Object> projectsPrepare = (Map<String, Object>) projectContext.get("projectsPrepare");

            int fileIndex = 0;
            for (Map.Entry<String, Object> fileEntry : projectsPrepare.entrySet()) {
                String filePath = fileEntry.getKey();
                Object fileValue = fileEntry.getValue();
                Path pathOfFilePath = Paths.get(filePath);
                String fileFolder = pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : "";
                String fileName = Paths.get(filePath).getFileName().toString();
                int extensionIdx = fileName.lastIndexOf('.');
                String extensionName = fileName.substring(extensionIdx + 1);
                String fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : fileName;

                // Files paths
                String fullFilePath = String.valueOf(FileUtils.absolutePathJoin(projectContext.get("rootFolder"), filePath));

                var fileMeta = new ConcurrentLinkedHashMap<String, Object>();

                // Context file/folder/project standard variables
                fileMeta.put("relativeFilePath", filePath);
                fileMeta.put("fullFilePath", fullFilePath);
                fileMeta.put("fileIndex", fileIndex);
                fileMeta.put("filePath", filePath);
                fileMeta.put("fileFolder", fileFolder);
                fileMeta.put("fileName", fileName);
                fileMeta.put("extensionName", extensionName);
                fileMeta.put("fileNameWithoutExtension", fileNameWithoutExtension);

                projectContext.putAll(fileMeta);
                projectContext.put("meta", fileMeta);

                List<TransformDto> history = new ConcurrentLinkedList<>();
                projectContext.put("fileHistory", history);
                Map<String, Object> fileMetadata = new ConcurrentLinkedHashMap<>();
                fileMetadata.put("meta", fileMeta);
                fileMetadata.put("history", history);
                filesMetadata.put(filePath, fileMetadata);

                String result = "";
                boolean anyException = false;
                try {
                    var naturalOutput = scriptService.autoEval((String) fileValue, history);
                    result = (naturalOutput instanceof String naturalOutputString) ? naturalOutputString : JsonUtils.writeAsJsonStringCircular(naturalOutput, true, false);
                } catch (Exception ex) {
                    anyException = true;
//                    log.error("Failed to evaluate {} during projectPrepare execution. Error message:\n{}", filePath, ex.getMessage());
                    throw new RuntimeException(ex);
                }

                if (result.startsWith("// Skipped/Empty File")) {
                    log.info("Skipping the file: " + fullFilePath);
                    filesMetadata.remove(filePath);
                } else {
                    if (anyException || !fileName.startsWith("_")) {
                        FileUtils.writeFile(Path.of(fullFilePath), result, false);
                    }
                }

                // Remove specific keys from projectContext
                Set<String> keysToRemove = fileMeta.keySet();
                keysToRemove.forEach(projectContext::remove);
                projectContext.remove("meta");

                fileIndex++;
            }
        }

        //
        // ProjectPrepare expansion
        if (recipe.get("projectPrepare") != null) {
            Map<String, Object> projectPrepareBaseModel = new ConcurrentLinkedHashMap<>();
            YamlUtils.traverse(projectPrepareBaseModel, recipe.get("projectPrepare"), "", key -> scriptService.evalIfSpEL(key));

            Map<String, Object> projectPrepareModel = new ConcurrentLinkedHashMap<>(projectPrepareBaseModel);

            int projectPrepareItemIndex = 0;
            for (Map.Entry<String, Object> projectPrepareItem : projectPrepareBaseModel.entrySet()) {
                Map<String, Object> expandedProjectPrepare = expand(projectContext, projectPrepareModel);
                projectContext.put("projectPrepare", Utils.convertToConcurrent(expandedProjectPrepare));
                projectPrepareItemIndex++;
            }

            Map<String, Object> projectPrepare = (Map<String, Object>) projectContext.get("projectPrepare");

            int fileIndex = 0;
            for (Map.Entry<String, Object> fileEntry : projectPrepare.entrySet()) {

                int projectIndex = 0;
                for (Map<String, Object> project : projects) {
                    String filePath = fileEntry.getKey();
                    Object fileValue = fileEntry.getValue();
                    Path pathOfFilePath = Paths.get(filePath);
                    String fileFolder = pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : "";
                    String fileName = Paths.get(filePath).getFileName().toString();
                    int extensionIdx = fileName.lastIndexOf('.');
                    String extensionName = fileName.substring(extensionIdx + 1);
                    String fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : fileName;

                    // Files paths
                    String fullFilePath = String.valueOf(FileUtils.absolutePathJoin(projectContext.get("rootFolder"), filePath));

                    var fileMeta = new ConcurrentLinkedHashMap<String, Object>();

                    // Context file/folder/project standard variables
                    fileMeta.put("project", project);
                    fileMeta.put("relativeFilePath", filePath);
                    fileMeta.put("fullFilePath", fullFilePath);
                    fileMeta.put("fileIndex", fileIndex);
                    fileMeta.put("filePath", filePath);
                    fileMeta.put("fileFolder", fileFolder);
                    fileMeta.put("fileName", fileName);
                    fileMeta.put("extensionName", extensionName);
                    fileMeta.put("fileNameWithoutExtension", fileNameWithoutExtension);

                    projectContext.put("meta", fileMeta);
                    projectContext.putAll(fileMeta);

                    List<TransformDto> history = new ConcurrentLinkedList<>();
                    projectContext.put("fileHistory", history);
                    Map<String, Object> fileMetadata = new ConcurrentLinkedHashMap<>();
                    fileMetadata.put("meta", fileMeta);
                    fileMetadata.put("history", history);
                    filesMetadata.put(filePath, fileMetadata);

                    String result = "";
                    boolean anyException = false;
                    try {
                        var naturalOutput = scriptService.autoEval((String) fileValue, history);
                        result = (naturalOutput instanceof String naturalOutputString) ? naturalOutputString : JsonUtils.writeAsJsonStringCircular(naturalOutput, true, false);
                    } catch (Exception ex) {
                        anyException = true;
//                        log.error("Failed to evaluate {} during projectPrepare execution. Error message:\n{}", filePath, ex.getMessage());
                        throw new RuntimeException(ex);
                    }

                    if (result.startsWith("// Skipped/Empty File")) {
                        log.info("Skipping the file: " + fullFilePath);
                        filesMetadata.remove(filePath);
                    } else {
                        if (anyException || !fileName.startsWith("_")) {
                            FileUtils.writeFile(Path.of(fullFilePath), result, false);
                        }
                    }

                    // Remove specific keys from projectContext
                    Set<String> keysToRemove = fileMeta.keySet();
                    keysToRemove.forEach(projectContext::remove);
                    projectContext.remove("meta");

                    projectIndex++;
                }
                fileIndex++;
            }
        }

        //
        // Model expansion
        for (int index = 0; index < projects.size(); index++) {
            Map<String, Object> project = projects.get(index);
            projectContext.put("project", project);

            Map<String, Object> projectBaseModel = new ConcurrentLinkedHashMap<>();
            YamlUtils.traverse(projectBaseModel, recipe.get("projectModel"), "", key -> scriptService.evalIfSpEL(key));

            Map<String, Object> projectModel = expand(projectContext, projectBaseModel);
            project.put("projectModel", Utils.convertToConcurrent(projectModel));
        }

        //
        // Creating tasks/files
        List<TaskMap> listOfTaskMap = new ConcurrentLinkedList<>();

        for (int index = 0; index < projects.size(); index++) {
            Map<String, Object> project = projects.get(index);
            Map<String, Object> projectModel = (Map<String, Object>) project.get("projectModel");

            // Ensure to always have a projectNormalizedName
            project.putIfAbsent("projectNormalizedName", project.get("name"));

            int fileIndex = 0;
            for (Map.Entry<String, Object> fileEntry : projectModel.entrySet()) {
                String filePath = fileEntry.getKey();
                Object fileValue = fileEntry.getValue();
                Path pathOfFilePath = Paths.get(filePath);
                String fileFolder = pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : "";
                String fileName = pathOfFilePath.getFileName().toString();
                int extensionIdx = fileName.lastIndexOf('.');
                String extensionName = fileName.substring(extensionIdx + 1);
                String fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : fileName;

                final int currentFileIndex = fileIndex;
                final int currentProjectIndex = index;
                final int projectModelSize = projectModel.size();

                Runnable task = () -> {
                    contextService.setFlowKey(flowKey);
                    var context = contextService.getProjectContext();

                    // Files paths
                    String relativeFilePath = String.valueOf(FileUtils.pathJoin(project.get("projectNormalizedName"), filePath));
                    String fullFilePath = String.valueOf(FileUtils.absolutePathJoin(context.get("rootFolder"), relativeFilePath));

                    var fileMeta = new ConcurrentLinkedHashMap<String, Object>();

                    // Context file/folder/project standard variables
                    fileMeta.put("relativeFilePath", relativeFilePath);
                    fileMeta.put("fullFilePath", fullFilePath);
                    fileMeta.put("project", project);
                    fileMeta.put("fileIndex", currentFileIndex);
                    fileMeta.put("filePath", filePath);
                    fileMeta.put("fileFolder", fileFolder);
                    fileMeta.put("fileName", fileName);
                    fileMeta.put("extensionName", extensionName);
                    fileMeta.put("fileNameWithoutExtension", fileNameWithoutExtension);

                    context.putAll(fileMeta);
                    context.put("meta", fileMeta);

                    if (currentFileIndex == 0) {
                        processAction(executorEvents, "beforeEachProject", null, null);
                    }

                    // File history
                    List<TransformDto> history = new ConcurrentLinkedList<>();
                    context.put("fileHistory", history);
                    Map<String, Object> fileMetadata = new ConcurrentLinkedHashMap<>();
                    fileMetadata.put("meta", fileMeta);
                    fileMetadata.put("history", history);
                    filesMetadata.put(relativeFilePath, fileMetadata);

                    processAction(executorEvents, "beforeEachFile", null, history);

                    String result = null;
                    try {
                        var naturalOutput = scriptService.autoEval((String) fileValue, history);
                        result = (naturalOutput instanceof String naturalOutputString) ? naturalOutputString : JsonUtils.writeAsJsonStringCircular(naturalOutput, true, false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    result = processAction(executorEvents, "afterEachFile", result, history);

                    if (fileName.startsWith("_") || result.startsWith("// Skipped/Empty File")) {
                        log.info("Skipping the file: " + fullFilePath);
                        filesMetadata.remove(relativeFilePath);
                    } else {
                        FileUtils.writeFile(Path.of(fullFilePath), result, false);
                    }

                    processAction(executorEvents, "afterEachFileSaved", result, null);

                    if (currentFileIndex == projectModelSize - 1) {
                        processAction(executorEvents, "afterEachProject", null, null);
                    }

                    // Remove specific keys from context
                    Set<String> keysToRemove = fileMeta.keySet();
                    keysToRemove.forEach(context::remove);
                    context.remove("meta");
                };

                TaskMap taskMap = new TaskMap("Project " + currentProjectIndex + " - " + filePath, task);
                listOfTaskMap.add(taskMap);

                fileIndex++;
            }
        }

        //
        // ProjectSuperModel expansion
        if (recipe.get("projectSuperModel") != null) {
            Map<String, Object> projectSuperModel = (Map<String, Object>) recipe.get("projectSuperModel");

            int fileIndex = 0;
            for (var fileEntry : projectSuperModel.entrySet()) {
                String filePath = fileEntry.getKey();
                Object fileValue = fileEntry.getValue();
                Path pathOfFilePath = Paths.get(filePath);
                String fileFolder = pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : "";
                String fileName = pathOfFilePath.getFileName().toString();
                int extensionIdx = fileName.lastIndexOf('.');
                String extensionName = fileName.substring(extensionIdx + 1);
                String fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : fileName;

                final int currentFileIndex = fileIndex;

                Runnable task = () -> {
                    contextService.setFlowKey(flowKey);
                    var context = contextService.getProjectContext();

                    // Files paths
                    var relativeFilePath = FileUtils.pathJoin(filePath).toString();
                    String fullFilePath = String.valueOf(FileUtils.absolutePathJoin(context.get("rootFolder"), relativeFilePath));

                    var fileMeta = new ConcurrentLinkedHashMap<String, Object>();

                    // Context file/folder/project standard variables
                    fileMeta.put("relativeFilePath", relativeFilePath);
                    fileMeta.put("fullFilePath", fullFilePath);
                    fileMeta.put("projects", projects);
                    fileMeta.put("fileIndex", currentFileIndex);
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
                    filesMetadata.put(relativeFilePath, fileMetadata);

                    processAction(executorEvents, "beforeEachFile", null, history);

                    String result = null;
                    try {
                        result = (String) scriptService.autoEval((String) fileValue, history);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    result = processAction(executorEvents, "afterEachFile", result, history);

                    if (fileName.startsWith("_") || result.startsWith("// Skipped/Empty File")) {
                        log.info("Skipping the file: " + fullFilePath);
                        filesMetadata.remove(relativeFilePath);
                    } else {
                        FileUtils.writeFile(Path.of(fullFilePath), result, false);
                    }

                    processAction(executorEvents, "afterEachFileSaved", result, null);

                    // Remove specific keys from context
                    Set<String> keysToRemove = fileMeta.keySet();
                    keysToRemove.forEach(context::remove);
                    context.remove("meta");
                };

                var taskMap = new TaskMap("Project Super Model - " + filePath, task);
                listOfTaskMap.add(taskMap);

                fileIndex++;
            }
        }

        if (executorEvents.get("afterAll") instanceof String afterAllEvent) {
            List<TaskMap> afterAllListOfTasks = null;
            try {
                afterAllListOfTasks = scriptService.getGroovyExecutor(afterAllEvent)
                        .prepareListOfTasks(applicationContext, flowKey);
            } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            listOfTaskMap.addAll(afterAllListOfTasks);
        }

        return listOfTaskMap;
    }

    private String processAction(Map<String, String> mapOfActions, String actionName, String input, List<TransformDto> history) {
        String action = mapOfActions.get(actionName);

        if (action == null) {
            return input;
        }

        log.info("Executing {}", actionName);

        String expression = action;
        if (expression.trim().startsWith("@@@")) {
            if (input != null) {
                expression += "\n" + input;
            }

            try {
                var naturalOutput = scriptService.autoEval(expression, history);
                return (naturalOutput instanceof String naturalOutputString) ? naturalOutputString : JsonUtils.writeAsJsonStringCircular(naturalOutput, true, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            ITransform groovyTransform;
            try {
                groovyTransform = scriptService.getGroovyTransform(actionName, expression);
            } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            var projectContext = applicationContext.getBean(ContextService.class).getProjectContext();
            return groovyTransform.execute(applicationContext, projectContext, input, null);
        }
    }

    private Map<String, Object> expand(Map<String, Object> projectContext, Map<String, Object> projectModel) {
        Map<String, Object> solved = new ConcurrentLinkedHashMap<>();

        for (Map.Entry<String, Object> entry : projectModel.entrySet()) {
            String filePath = entry.getKey();
            Object fileValue = entry.getValue();

            projectContext.put("filePath", filePath);
            String fileName = Paths.get(filePath).getFileName().toString();
            projectContext.put("fileName", fileName);

            evaluate(fileValue, filePath, solved);

            projectContext.remove("filePath");
            projectContext.remove("fileName");
        }

        return solved;
    }

    public void evaluate(Object object, String key, Map<String, Object> targetMap) {
        key = scriptService.evalIfSpEL(Utils.castValue(key));

        if (object instanceof String) {
            if (scriptService.isValidSpEL((String) object)) {
                String expression = (String) object;
                Object result = scriptService.evalIfSpEL(Utils.castValue(expression));
                if (result instanceof Map || result instanceof List) {
                    Map<String, Object> subItems = new ConcurrentLinkedHashMap<>();
                    YamlUtils.traverse(subItems, result, key, key2 -> scriptService.evalIfSpEL(key2));

                    for (Map.Entry<String, Object> entry : subItems.entrySet()) {
                        targetMap.put(Paths.get(entry.getKey()).toString(), entry.getValue());
                    }
                } else if (result instanceof String) {
                    log.info("Lazy mode, expression will be evaluated again during execution");
                    targetMap.put(key, expression);
                } else if (result == null) {
                    throw new IllegalStateException("ProjectModel expansion failed, due to an object null evaluated from expression: " + expression);
                } else {
                    throw new IllegalStateException("ProjectModel expansion failed, due to an object of type " + result.getClass() + " evaluated from expression: " + expression);
                }
            } else {
                targetMap.put(key, object);
            }
        } else {
            targetMap.put(key, object);
        }
    }
}