import com.capco.brsp.synthesisengine.service.*
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.springframework.context.ApplicationContext
import groovy.json.JsonSlurper

class PowerBuilderBeforeAll implements IExecutor {
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
        final def prompts = projectContext.recipe.prompts as Map<String, Object>

        def vars = projectContext.recipe?.vars as Map<String, Object>

        // We read the cypher query to get the program names from the recipe
        // In the next step we execute the query in the autoEval function 
        def programNamesCypherQuery = templates.programNames as String
        def programNamesResult = scriptService.autoEval(programNamesCypherQuery)
        def programNamesList = Eval.me(programNamesResult) as List<String>

        // Get PowerBuilder source code (Neo4j)
        def rawCodeCypherQuery = templates.rawCode as String
        // TODO clean up the response
        def rawCodeResult = scriptService.autoEval(rawCodeCypherQuery)

        projectContext.programNames = programNamesList
        projectContext.rawCode = rawCodeResult

        // Create blueprint (Prompt)
        def bluePrintPrompt = prompts.blueprint as String
        // TODO clean up the response
        def bluePrintPromptResult = scriptService.autoEval(bluePrintPrompt)

        projectContext.bluePrintPromptResult = bluePrintPromptResult

        def dtoListPrompt = prompts.dtolist as String
        def dtoListPromptResult = scriptService.autoEval(dtoListPrompt)

        projectContext.dtoListPromptResult=dtoListPromptResult

        List<Map> dtos = new JsonSlurper().parseText(dtoListPromptResult)
        projectContext.dtoList = dtos
        List<String> dtoNames = dtos.collect { it.name as String }

        projectContext.dtoKeyLists = dtoNames

        def entitylistPrompt = prompts.entitylist as String
        def entitylistPromptResult = scriptService.autoEval(entitylistPrompt)

        projectContext.entitylistPromptResult = entitylistPromptResult

        List<Map> entities = new JsonSlurper().parseText(entitylistPromptResult)
        projectContext.entities = entities
        List<String> entitiesNames = entities.collect { it.name as String }

        projectContext.entitiesNames = entitiesNames
        
        def crawlerEvaluatedParams = new ConcurrentLinkedHashMap<String, Object>()
        def crawlerParams = vars.crawlerParams as Map<String, Object>
        crawlerParams.eachWithIndex { Map.Entry<String, Object> parameterEntry, int parameterIndex ->
            def parameterName = parameterEntry.getKey()
            def parameterValue = scriptService.evalIfSpEL(parameterEntry.getValue())

            crawlerEvaluatedParams.put(parameterName, parameterValue)
        }

        projectContext.crawlerEvaluatedParams = crawlerEvaluatedParams

        if (crawlerEvaluatedParams?.splitByProgram) {
            projectContext.programNames.each { programName ->
                def project = new ConcurrentLinkedHashMap<>()
                project.name = programName

                project.projectNormalizedName = programName
                                                .replaceAll('[^a-zA-Z0-9]+', '_')
                                                .toLowerCase()
                project.dtos = dtoNames
                project.dtos = project.dtos.collect { it + '.java' }
                project.entities = entitiesNames
                project.entities = project.entities.collect { it + '.java' }  
                projects.add(project)
            }
        } else {
            def project = new ConcurrentLinkedHashMap<>()
            project.name = "powerbilder-app"
            project.projectNormalizedName = "powerbilder-app"
            project.dtos = dtoNames
            project.dtos = project.dtos.collect { it + '.java' }
            project.entities = entitiesNames
            project.entities = project.entities.collect { it + '.java' }
            projects.add(project)
        }

        if (!projectContext.containsKey('blueprint') || projectContext.blueprint == null) {
                projectContext.blueprint = []      
            }


        return "OK"
    }
}