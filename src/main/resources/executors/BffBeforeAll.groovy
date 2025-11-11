package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import org.springframework.context.ApplicationContext

import java.util.regex.Matcher
import java.util.regex.Pattern

class BffBeforeAll implements IExecutor {
    SuperService superService
    ScriptService scriptService
//TODO: Create regex for C# for when $api.config.options.backendLanguage == 'csharp'
    static final Pattern REQUEST_BODY_CLASS = ~/@RequestBody\s+([A-Za-z0-9_$.]+)\s+\w+\s*[),]/
    //static final Pattern REQUEST_BODY_CLASS =~/(?:@RequestBody|\[FromBody\])\s+([A-Za-z0-9_$.\[\]<>?,\s]+?)\s+\w+\s*[),]/
    static final Pattern RESPONSE_ENTITIES = ~/ResponseEntity\s*<\s*([A-Za-z0-9_$.]+)\s*>/


    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        println "Starting execution of BffBeforeAll in dynamic script"

        def backendFilesTree = getAndValidateFileTree(projectContext, 'backendFilesTree')
        def frontendFilesTree = getAndValidateFileTree(projectContext, 'frontendFilesTree')

        def filesCategories = [
                "backendPort": [],
                "dtos"       : [],
                "models"     : [],
                "dataObjects": [],
                "screens"    : [],
                "controllers": []
        ]
        def screenRoutes = [:]
        def requestBodyDataObjects = [:]
        def responseEntities = [:]

        processFiles(backendFilesTree, filesCategories, screenRoutes, requestBodyDataObjects, responseEntities)
        processFiles(frontendFilesTree, filesCategories, screenRoutes, requestBodyDataObjects, responseEntities)

        projectContext.putAll(filesCategories)
        projectContext.put("screenRoutes", screenRoutes)
        projectContext.put("requestBodyDataObjects", requestBodyDataObjects)
        projectContext.put("responseEntities", responseEntities)

        filesCategories.each { category, files ->
            println "${category.capitalize()} : ${files.size()} files found."
            files.each { println(" - $it") }
        }

        return "BFF Before All execution completed successfully."
    }

    static def getAndValidateFileTree(Map<String, Object> projectContext, String key) {
        def fileTree = projectContext.get(key)
        if (!fileTree) {
            throw new IllegalArgumentException("Missin '${key}' in project context!")
        }
        return fileTree
    }

    static boolean isController(String path, String content) {
        return path.contains('controller') || path.toLowerCase().contains('controller')
                || path.contains('Controllers') || path.toLowerCase().contains('Controllers')
    }

    static boolean isBackendPort(String path, String content) {
        return path.contains('application.yaml') || path.toLowerCase().contains('application.yaml')
    }

    static boolean isDto(String path, String content) {
        return path.contains('dto') || path.toLowerCase().contains('dto')
    }

    static boolean isModel(String path, String content) {
        return path.contains('Models') || path.toLowerCase().contains('Models') ||
                path.contains('Entities') || path.toLowerCase().contains('Entities') ||
                path.contains('db') || path.toLowerCase().contains('db')
    }

    static boolean isDataObject(String path, String content) {
        return path.contains('Models') || path.toLowerCase().contains('Models') ||
                path.contains('Entities') || path.toLowerCase().contains('Entities') ||
                path.contains('db') || path.toLowerCase().contains('db') ||
                path.contains('dto') || path.toLowerCase().contains('dto') ||
                path.contains('file') || path.toLowerCase().contains('file') &&
                !path.contains('utils/') && !path.contains('annotation/')
    }

    static boolean isScreen(String path, String content) {
        return path.contains('screens')
    }

    static boolean isScreenRoute(String path, String content) {
        return path.contains('screens') && content?.contains('url')
    }

    void processFiles(Map<String, String> filesTree, Map<String, List<String>> categories,
                      Map<String, Map<String, String>> screenRoutes,
                      Map<String, String> requestBodyDataObjects,
                      Map<String, String> responseEntities) {
        filesTree.each { path, content ->
            File file = new File(path)
            if (file.isDirectory() || !content || !path) {
                return
            }
            classifyFile(path, content, categories, screenRoutes, requestBodyDataObjects, responseEntities)
        }
    }

    void classifyFile(String path, String content, Map<String, List<String>> categories,
                      Map<String, Map<String, String>> screenRoutes,
                      Map<String, String> requestBodyDataObjects,
                      Map<String, String> responseEntities) {
        def classification = [
                "dtos"        : BffBeforeAll.&isDto,
                "models"      : BffBeforeAll.&isModel,
                "dataObjects" : BffBeforeAll.&isDataObject,
                "backendPort" : BffBeforeAll.&isBackendPort,
                "screenRoutes": BffBeforeAll.&isScreenRoute,
                "screens"     : BffBeforeAll.&isScreen,
                "controllers" : BffBeforeAll.&isController
        ]

        def classifiedState = [wasClassified: false]
        classification.each { category, condition ->
            if (condition(path, content) && category != "screenRoutes") {
                categories[category] << ("${path}" as String)
                classifiedState.wasClassified = true
            } else if (condition(path, content) && category == "screenRoutes") {
                screenRoutes[path.split("screens/")[1].split(".json")[0]] = content
                classifiedState.wasClassified = true
            }
        }
        if (isController(path, content)) {
            Matcher m = REQUEST_BODY_CLASS.matcher(content)
            while (m.find()) {
                String className = m.group(1)
                requestBodyDataObjects.put(className, path)
            }
        }
        if (isController(path, content)) {
            Matcher e = RESPONSE_ENTITIES.matcher(content)
            while (e.find()) {
                String className = e.group(1)
                responseEntities.put(className, path)
            }
        }
    }
}
