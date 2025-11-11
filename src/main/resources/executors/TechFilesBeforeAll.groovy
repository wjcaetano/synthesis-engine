import com.capco.brsp.synthesisengine.service.*
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.springframework.context.ApplicationContext

class TechFilesBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        def toolsFunction = applicationContext.getBean(ToolsFunction.class)
        def projectUUID = projectContext.get("projectUUID") as UUID

        // BeforeAll ensure to create a fresh 'projects' object
        final def projects = projectContext.compute('projects', (k, v) -> new ConcurrentLinkedList<>()) as ConcurrentLinkedList

        final def templates = projectContext.recipe.templates as Map<String, Object>

        def vars = projectContext.recipe?.vars as Map<String, Object>
        def programmingLanguage = projectContext['$api'].configs.options.programming_language as String
        def crawlerURL = (vars.crawlerURL as String).replace('{projectUUID}', projectUUID.toString()) + "/loadAll"
        def crawlerContext = vars.crawlerContext[programmingLanguage] as Map<String, Object>

        toolsFunction.apiCall(crawlerURL, 'POST', crawlerContext, null)

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

        // All Program Names
        def programNamesCypherQuery = templates.programNames[programmingLanguage] as String
        def programNamesResult = scriptService.autoEval(programNamesCypherQuery)
        projectContext.programNames = safeReadAsList(programNamesResult)

        def crawlerEvaluatedParams = new ConcurrentLinkedHashMap<String, Object>()
        def crawlerParams = vars.crawlerParams as Map<String, Object>
        crawlerParams.eachWithIndex { Map.Entry<String, Object> parameterEntry, int parameterIndex ->
            def parameterName = parameterEntry.getKey()
            def parameterValue = scriptService.evalIfSpEL(parameterEntry.getValue())

            crawlerEvaluatedParams.put(parameterName, parameterValue)
        }

        projectContext.crawlerEvaluatedParams = crawlerEvaluatedParams

        if (crawlerEvaluatedParams?.splitByProgram) {
            projectContext.programNames.eachWithIndex { String programName, int programIndex ->
                final def project = new ConcurrentLinkedHashMap<>()
                project.name = programName
                projects.add(project)
            }
        } else {
            final def project = new ConcurrentLinkedHashMap<>()
            project.name = "tech-files"
            projects.add(project)
        }

        return "OK"
    }
}