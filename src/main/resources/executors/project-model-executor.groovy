import com.capco.brsp.synthesisengine.dto.TaskMap
import com.capco.brsp.synthesisengine.dto.TransformDto
import com.capco.brsp.synthesisengine.service.ContextService
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.*
import org.springframework.context.ApplicationContext

import java.nio.file.Paths

class ProjectModelExecutor implements IExecutor {
    ApplicationContext applicationContext = null
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    @Override
    List<TaskMap> prepareListOfTasks(ApplicationContext applicationContext, String flowKey) {
        println "Starting the executor: project-model-executor"

        //
        // Beans
        this.applicationContext = applicationContext
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        def contextService = applicationContext.getBean(ContextService.class)

        def projectContext = contextService.getProjectContext()
        def recipe = projectContext.recipe as Map<String, Object>
        def executorEvents = (recipe?.executorEvents ?: new ConcurrentLinkedHashMap<>()) as Map<String, String>
        def filesMetadata = Utils.convertToConcurrent((projectContext.get("files_metadata") ?: new ConcurrentLinkedHashMap<>())) as ConcurrentLinkedHashMap<String, Object>

        projectContext.put("files_metadata", filesMetadata)

        //
        // Loading custom transformAction scripts
        Map<String, String> transforms = (recipe.transforms ?: new ConcurrentLinkedHashMap<>()) as Map<String, String>
        transforms.each { key, val ->
            val = Utils.castValue(val) as String
            scriptService.getGroovyTransform(key, val)
        }

        //
        // Loading custom executor scripts - TODO: iterate over any levels looking for a @@@groovy transformAction
        Map<String, String> allMapTemplates = (recipe.prompts ?: new ConcurrentLinkedHashMap<>()) as Map<String, String>
        allMapTemplates.putAll(executorEvents)
        allMapTemplates.each { key, val ->
            if (val.contains("@@@groovy")) {
                // TODO: Review the !allGroovys.isEmpty(), because if we try to run a template with more than one @@@groovy anotation, and one of them is without a file parameter, then it will be ignored again
                def allGroovys = Utils.getAllRegexGroup(val, "@@@groovy\\((.+)\\)", 1)
                if (Utils.isDebugMode() && !allGroovys.isEmpty()) {
                    allGroovys.forEach { groovyScriptPath ->
                        {
                            groovyScriptPath = Utils.castValue(groovyScriptPath) as String
                            println "Trying to parse the executor: " + groovyScriptPath
                            scriptService.getGroovyExecutor("src/main/resources/executors/" + groovyScriptPath)
                            println "Successfully parsed the executor: " + groovyScriptPath
                        }
                    }
                } else {
                    def found = false
                    def groovyScript = val.readLines().findAll { line ->
                        if (!found && !line.stripLeading().startsWith('@@@')) {
                            found = true
                        }
                        found
                    }.join(System.lineSeparator())
                    def executorClassName = Utils.getRegexGroup(groovyScript, "\\bclass\\s+(\\w+)\\s+implements\\s+IExecutor\\s+\\{", 1)
                    println "Trying to parse the executor: " + executorClassName
                    scriptService.getGroovyExecutor(groovyScript)
                    println "Successfully parsed the executor: " + executorClassName
                }
            }
        }

        // TODO: try to put all of it as a Task like afterAll (I doubt it will be possible! Let's see)
        processAction(executorEvents, 'beforeAll', null, null)

        if (!projectContext.projects) {
            def projectsReference = recipe.projectsReference
            if (projectsReference instanceof String) {
                def evaluatedProjectsReference = scriptService.autoEval(projectsReference)
                if (evaluatedProjectsReference instanceof String) {
                    projectContext.projects = JsonUtils.readAsList(evaluatedProjectsReference) as List<Map<String, Object>>
                } else {
                    projectContext.projects = evaluatedProjectsReference as List<Map<String, Object>>
                }
            } else {
                projectContext.projects = projectsReference
            }
        }

        final def projects = projectContext.projects as List<Map<String, Object>>
        if (projects == null) {
            throw new IllegalStateException("Not able to identify the list of projects! Current value: ${projectContext.projects}".toString())
        }

        projects.eachWithIndex{ Map<String, Object> project, int i ->
            project.put('index', i)
        }

        //
        // ProjectsPrepare expansion
        if (recipe.projectsPrepare) {
            Map<String, Object> projectsPrepareBaseModel = new ConcurrentLinkedHashMap<>()
            YamlUtils.traverse(projectsPrepareBaseModel, recipe.projectsPrepare, "", key -> scriptService.evalIfSpEL(key))

            Map<String, Object> projectsPrepareModel = new ConcurrentLinkedHashMap<String, Object>(projectsPrepareBaseModel)
            projectsPrepareBaseModel.eachWithIndex { Map.Entry<String, Object> projectsPrepareItem, int projectsPrepareItemIndex ->
                var expandedProjectsPrepare = expand(projectContext, projectsPrepareModel)
                projectContext.put('projectsPrepare', Utils.convertToConcurrent(expandedProjectsPrepare))
            }

            Map<String, Object> projectsPrepare = projectContext.projectsPrepare as Map<String, Object>
            projectsPrepare.eachWithIndex { Map.Entry<String, Object> fileEntry, int fileIndex ->
                def filePath = fileEntry.getKey()
                def fileValue = fileEntry.getValue()
                def fileFolder = Paths.get(filePath).getParent().toString()
                String fileName = Paths.get(filePath).getFileName().toString()
                def extensionIdx = fileName.lastIndexOf('.')
                def extensionName = fileName.substring(extensionIdx + 1)
                def fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : ""

                // Files paths
                var fullFilePath = FileUtils.absolutePathJoin(projectContext.rootFolder, filePath)

                // Context file/folder/project standard variables
                projectContext.put("relativeFilePath", filePath)
                projectContext.put("fullFilePath", fullFilePath)
                projectContext.put("fileIndex", fileIndex)
                projectContext.put('filePath', filePath)
                projectContext.put('fileFolder', fileFolder)
                projectContext.put('fileName', fileName)
                projectContext.put('extensionName', extensionName)
                projectContext.put('fileNameWithoutExtension', fileNameWithoutExtension)

                List<TransformDto> history = new ConcurrentLinkedList<>()
                projectContext.put('fileHistory', history)
                def fileMetadata = new ConcurrentLinkedHashMap<String, Object>()
                fileMetadata.put("history", history)
                filesMetadata.put(filePath.toString(), fileMetadata)

                def result = ""
                def anyException = false
                try {
                    result = scriptService.autoEval(fileValue as String, history) as String
                } catch (Exception ignored) {
                    anyException = true
                    println "Failed to evaluate ${filePath} during projectsPrepare execution"
                }

                if (result.startsWith("// Skipped/Empty File")) {
                    println "Skipping the file: ${fullFilePath}"
                    filesMetadata.remove(filePath.toString())
                } else {
                    if (anyException || !fileName.startsWith("_")) {
                        FileUtils.writeFile(fullFilePath, result ?: "", false)
                    }
                }

                projectContext.removeAll { ['relativeFilePath', 'relativeFilePath', 'fullFilePath', 'project', 'fileIndex', 'filePath', 'fileFolder', 'fileName', 'extensionName', 'fileNameWithoutExtension'].contains(it) }
            }
        }

        //
        // ProjectPrepare expansion
        if (recipe.projectPrepare) {
            Map<String, Object> projectPrepareBaseModel = new ConcurrentLinkedHashMap<>()
            YamlUtils.traverse(projectPrepareBaseModel, recipe.projectPrepare, "", key -> scriptService.evalIfSpEL(key))

            Map<String, Object> projectPrepareModel = new ConcurrentLinkedHashMap<String, Object>(projectPrepareBaseModel)
            projectPrepareBaseModel.eachWithIndex { Map.Entry<String, Object> projectPrepareItem, int projectPrepareItemIndex ->
                var expandedProjectPrepare = expand(projectContext, projectPrepareModel)
                projectContext.put('projectPrepare', Utils.convertToConcurrent(expandedProjectPrepare))
            }

            Map<String, Object> projectPrepare = projectContext.projectPrepare as Map<String, Object>
            projectPrepare.eachWithIndex { Map.Entry<String, Object> fileEntry, int fileIndex ->

                projects.eachWithIndex { Map<String, Object> project, int projectIndex ->
                    def filePath = fileEntry.getKey()
                    def fileValue = fileEntry.getValue()
                    def pathOfFilePath = Paths.get(filePath)
                    def fileFolder = pathOfFilePath.getParent() != null ? pathOfFilePath.getParent().toString() : ""
                    String fileName = Paths.get(filePath).getFileName().toString()
                    def extensionIdx = fileName.lastIndexOf('.')
                    def extensionName = fileName.substring(extensionIdx + 1)
                    def fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : ""

                    // Files paths
                    var fullFilePath = FileUtils.absolutePathJoin(projectContext.rootFolder, filePath)

                    // Context file/folder/project standard variables
                    projectContext.put("project", project)
                    projectContext.put("relativeFilePath", filePath)
                    projectContext.put("fullFilePath", fullFilePath)
                    projectContext.put("fileIndex", fileIndex)
                    projectContext.put('filePath', filePath)
                    projectContext.put('fileFolder', fileFolder)
                    projectContext.put('fileName', fileName)
                    projectContext.put('extensionName', extensionName)
                    projectContext.put('fileNameWithoutExtension', fileNameWithoutExtension)

                    List<TransformDto> history = new ConcurrentLinkedList<>()
                    projectContext.put('fileHistory', history)
                    def fileMetadata = new ConcurrentLinkedHashMap<String, Object>()
                    fileMetadata.put("history", history)
                    filesMetadata.put(filePath.toString(), fileMetadata)

                    def result = ""
                    def anyException = false
                    try {
                        result = scriptService.autoEval(fileValue as String, history) as String
                    } catch (Exception ignored) {
                        anyException = true
                        println "Failed to evaluate ${filePath} during projectPrepare execution"
                    }

                    if (result.startsWith("// Skipped/Empty File")) {
                        println "Skipping the file: ${fullFilePath}"
                        filesMetadata.remove(filePath.toString())
                    } else {
                        if (anyException || !fileName.startsWith("_")) {
                            FileUtils.writeFile(fullFilePath, result ?: "", false)
                        }
                    }

                    projectContext.removeAll { ['relativeFilePath', 'relativeFilePath', 'fullFilePath', 'project', 'fileIndex', 'filePath', 'fileFolder', 'fileName', 'extensionName', 'fileNameWithoutExtension'].contains(it) }
                }
            }
        }

        //
        // Model expansion
        projects.eachWithIndex { Map<String, Object> project, int index ->
            projectContext.put('project', project)

            Map<String, Object> projectBaseModel = new ConcurrentLinkedHashMap<>()
            YamlUtils.traverse(projectBaseModel, recipe.projectModel, "", key -> scriptService.evalIfSpEL(key))

            Map<String, Object> projectModel = expand(projectContext, projectBaseModel)
            project.put('projectModel', Utils.convertToConcurrent(projectModel))
        }

        //
        // Creating tasks/files
        List<TaskMap> listOfTaskMap = new ConcurrentLinkedList<>()
        projects.eachWithIndex { Map<String, Object> project, int index ->
            Map<String, Object> projectModel = project.projectModel as Map<String, Object>

            // Ensure to always have a projectNormalizedName
            project.putIfAbsent('projectNormalizedName', project.name)

            projectModel.eachWithIndex { fileEntry, fileIndex ->
                {  
                    def filePath = fileEntry.getKey()
                    def fileValue = fileEntry.getValue()
                    def fileFolder = Paths.get(filePath).getParent().toString()
                    def fileName = Paths.get(filePath).getFileName().toString()
                    def extensionIdx = fileName.lastIndexOf('.')
                    def extensionName = fileName.substring(extensionIdx + 1)
                    def fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : ""

                    Runnable task = {

                        contextService.setFlowKey(flowKey)
                        def context = contextService.getProjectContext()

                        // Files paths
                        var relativeFilePath = FileUtils.pathJoin(project.projectNormalizedName, filePath)
                        var fullFilePath = FileUtils.absolutePathJoin(context.rootFolder, relativeFilePath)

                        // Context file/folder/project standard variables
                        context.put("relativeFilePath", relativeFilePath)
                        context.put("fullFilePath", fullFilePath)
                        context.put("project", project)
                        context.put("fileIndex", fileIndex)
                        context.put('filePath', filePath)
                        context.put('fileFolder', fileFolder)
                        context.put('fileName', fileName)
                        context.put('extensionName', extensionName)
                        context.put('fileNameWithoutExtension', fileNameWithoutExtension)

                        if (fileIndex == 0) {
                            processAction(executorEvents, 'beforeEachProject', null, null)
                        }

                        // File history
                        List<TransformDto> history = new ConcurrentLinkedList<>()
                        context.put('fileHistory', history)
                        def fileMetadata = new ConcurrentLinkedHashMap<String, Object>()
                        fileMetadata.put("history", history)
                        filesMetadata.put(relativeFilePath.toString(), fileMetadata)

                        processAction(executorEvents, 'beforeEachFile', null, history)

                        def result = scriptService.autoEval(fileValue as String, history) as String

                        result = processAction(executorEvents, 'afterEachFile', result, history) ?: ""

                        if (fileName.startsWith("_") || result.startsWith("// Skipped/Empty File")) {
                            println "Skipping the file: ${fullFilePath}"
                            filesMetadata.remove(relativeFilePath.toString())
                        } else {
                            FileUtils.writeFile(fullFilePath, result ?: "", false)
                        }

                        processAction(executorEvents, 'afterEachFileSaved', result, null)

                        if (fileIndex == projectModel.size() - 1) {
                            processAction(executorEvents, 'afterEachProject', null, null)
                        }

                        context.removeAll {['relativeFilePath', 'relativeFilePath', 'fullFilePath', 'project', 'fileIndex', 'filePath', 'fileFolder', 'fileName', 'extensionName', 'fileNameWithoutExtension'].contains(it) }
                    }

                    def taskMap = new TaskMap("Project ${index} - ${filePath}".toString(), task)
                    listOfTaskMap.add(taskMap)
                }
            }
        }

        //
        // ProjectSuperModel expansion
        if (recipe.projectSuperModel) {
            Map<String, Object> projectSuperModel = recipe.projectSuperModel as Map<String, Object>

            projectSuperModel.eachWithIndex { fileEntry, fileIndex ->
                {
                    def filePath = fileEntry.getKey()
                    def fileValue = fileEntry.getValue()
                    def fileFolder = Paths.get(filePath).getParent().toString()
                    def fileName = Paths.get(filePath).getFileName().toString()
                    def extensionIdx = fileName.lastIndexOf('.')
                    def extensionName = fileName.substring(extensionIdx + 1)
                    def fileNameWithoutExtension = extensionIdx > -1 ? fileName.substring(0, extensionIdx) : ""

                    Runnable task = {

                        contextService.setFlowKey(flowKey)
                        def context = contextService.getProjectContext()

                        // Files paths
                        var relativeFilePath = FileUtils.pathJoin(filePath)
                        var fullFilePath = FileUtils.absolutePathJoin(context.rootFolder, relativeFilePath)

                        // Context file/folder/project standard variables
                        context.put("relativeFilePath", relativeFilePath)
                        context.put("fullFilePath", fullFilePath)
                        context.put("projects", projects)
                        context.put("fileIndex", fileIndex)
                        context.put('filePath', filePath)
                        context.put('fileFolder', fileFolder)
                        context.put('fileName', fileName)
                        context.put('extensionName', extensionName)
                        context.put('fileNameWithoutExtension', fileNameWithoutExtension)

                        // File history
                        List<TransformDto> history = new ConcurrentLinkedList<>()
                        context.put('fileHistory', history)
                        def fileMetadata = new ConcurrentLinkedHashMap<String, Object>()
                        fileMetadata.put("history", history)
                        filesMetadata.put(relativeFilePath.toString(), fileMetadata)

                        processAction(executorEvents, 'beforeEachFile', null, history)

                        def result = scriptService.autoEval(fileValue as String, history) as String

                        result = processAction(executorEvents, 'afterEachFile', result, history) ?: ""

                        if (fileName.startsWith("_") || result.startsWith("// Skipped/Empty File")) {
                            println "Skipping the file: ${fullFilePath}"
                            filesMetadata.remove(relativeFilePath.toString())
                        } else {
                            FileUtils.writeFile(fullFilePath, result ?: "", false)
                        }

                        processAction(executorEvents, 'afterEachFileSaved', result, null)

                        context.removeAll {['relativeFilePath', 'relativeFilePath', 'fullFilePath', 'projects', 'fileIndex', 'filePath', 'fileFolder', 'fileName', 'extensionName', 'fileNameWithoutExtension'].contains(it) }
                    }

                    def taskMap = new TaskMap("Project Super Model - ${filePath}".toString(), task)
                    listOfTaskMap.add(taskMap)
                }
            }
        }

        if (executorEvents.afterAll instanceof String) {
            def afterAllListOfTasks = scriptService.getGroovyExecutor(executorEvents.afterAll).prepareListOfTasks(applicationContext, flowKey)
            listOfTaskMap.addAll(afterAllListOfTasks)
        }

        return listOfTaskMap
    }

    String processAction(Map<String, String> mapOfActions, String actionName, String input, List<TransformDto> history) {
        def action = mapOfActions[actionName]

        if (action == null) {
            return input
        }

        println "Executing ${actionName}"

        String expression = action
        if (expression.trim().startsWith("@@@")) {
            if (input != null) {
                expression += "\n" + input
            }

            return scriptService.autoEval(expression, history ?: new ConcurrentLinkedList<>() as List<TransformDto>)
        } else {
            def groovyTransform = scriptService.getGroovyTransform(actionName, expression)
            def projectContext = applicationContext.getBean(ContextService.class).getProjectContext()
            return groovyTransform.execute(applicationContext, projectContext, input, null)
        }
    }

    Map<String, Object> expand(Map<String, Object> projectContext, Map<String, Object> projectModel) {
        Map<String, Object> solved = new ConcurrentLinkedHashMap<>()
        projectModel.each { filePath, fileValue ->
            {
                projectContext.put('filePath', filePath)
                def fileName = Paths.get(filePath).getFileName().toString()
                projectContext.put('fileName', fileName)

                evaluate(fileValue, filePath, solved)

                projectContext.remove('filePath')
                projectContext.remove('fileName')
            }
        }

        return solved
    }

    def evaluate(Object object, String key, Map<String, Object> targetMap) {
        key = scriptService.evalIfSpEL(Utils.castValue(key))

        if (object instanceof String) {
            if (scriptService.isValidSpEL((String) object)) {
                String expression = (String) object
                try {
                    var result = scriptService.evalIfSpEL(Utils.castValue(expression))
                    if (result instanceof Map || result instanceof List) {
                        def subItems = new ConcurrentLinkedHashMap<String, Object>()
                        YamlUtils.traverse(subItems, result, key, key2 -> scriptService.evalIfSpEL(key2))
                        subItems.each { it ->
                            targetMap.put(Paths.get(it.key).toString(), it.value)
                        }
                    } else {
                        targetMap.put(key, result)
                    }
                } catch (Exception ignored) {
                    println "Expression '${expression}' exception during expand: ${ignored.getMessage()}"
                    ignored.printStackTrace()
                    targetMap.put(key, null)
                }
            } else {
                targetMap.put(key, object)
            }
        } else {
            targetMap.put(key, object)
        }
    }
}
