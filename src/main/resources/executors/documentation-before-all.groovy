import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.*
import org.springframework.context.ApplicationContext

class DocumentationBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        // BeforeAll ensure to create a fresh 'projects' object
        final def projects = projectContext.compute('projects', (k, v) -> new ConcurrentLinkedList<>()) as ConcurrentLinkedList
        final def project = new ConcurrentLinkedHashMap<>()
        project.name = "documentation"
        projects.add(project)

        final def templates = projectContext.recipe.templates as Map<String, Object>
        final def cypherQueries = templates.cypherQueries as Map<String, Object>
        def safeReadAsList = (input) -> {
            if (!JsonUtils.isValidJson(input)) {
                throw new RuntimeException("Not a valid JSON input:\n" + String.valueOf(input))
            }

            try {
                return JsonUtils.readAsList(input as String)
            } catch (Exception ignored) {
                throw new RuntimeException("Input is not a JSON list, even being a valid JSON:\n" + String.valueOf(input))
            }
        }

        project.scaffold = templates.scaffold as String

        project.logInfo = { msg -> println "${msg}" } as freemarker.template.TemplateMethodModelEx

        def generateBy = projectContext['$api']?.configs?.options?.generateBy as String
        // Intro Summarization
        if (generateBy == "capabilities") {
            try {
                def introCypherQuery = cypherQueries.intro as String
                def resultIntro = scriptService.autoEval(introCypherQuery)
                project.intro = safeReadAsList(resultIntro)[0].fullIntro
            } catch (Exception e) {
                throw new RuntimeException("Error in processing 'intro' Cypher Query result.\n", e)
            }
            // All Capabilities Summarization
            try {
                def allCapabilitiesCypherQuery = cypherQueries.allCapabilities as String
                def resultAllCapabilities = scriptService.autoEval(allCapabilitiesCypherQuery)
                project.capabilities = safeReadAsList(resultAllCapabilities)
            } catch (Exception e) {
                throw new RuntimeException("Error in processing 'allCapabilities' Cypher Query result.\n", e)
            }
        }

        // All callableDefinitions Summarization
        if (generateBy == "callableDefinitions") {
            try {
                def allCallableDefinitionsCypherQuery = cypherQueries.allCallableDefinitions as String
                def resultAllCallableDefinitions = scriptService.autoEval(allCallableDefinitionsCypherQuery)
                project.callableDefinitions = safeReadAsList(resultAllCallableDefinitions)
            } catch (Exception e) {
                throw new RuntimeException("Error in processing 'allCallableDefinitions' Cypher Query result.\n", e)
            }
        }

        // All Documents Summarization
        try {
            def allDocumentsCypherQuery = cypherQueries.allDocuments as String
            def resultAllDocuments = scriptService.autoEval(allDocumentsCypherQuery)
            //project.documentsHierarchy = safeReadAsList(resultAllDocuments)
        } catch (Exception e) {
            throw new RuntimeException("Error in processing 'AllDocuments' Cypher Query result.\n", e)
        }
    }
}