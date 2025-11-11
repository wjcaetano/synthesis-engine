import com.capco.brsp.synthesisengine.service.*
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class ReportBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        def contextService = applicationContext.getBean(ContextService.class)
//        def projectUUID = contextService.getProjectContext().get("projectUUID") as UUID
        def tempOriginalProjectsContent = Utils.decodeBase64ToString(Objects.requireNonNull(projectContext['$api'].files['monolith_decomposition_report.json'], "File 'monolith_decomposition_report.json' is missing!") as String)
        def monolithDecompositionReport = tempOriginalProjectsContent instanceof List ? tempOriginalProjectsContent : JsonUtils.readAsList(tempOriginalProjectsContent)

        projectContext['$api'].files.clusters = monolithDecompositionReport

        def templates = projectContext.recipe.templates as Map<String, Object>
        def reportsTemplates = templates.reports as List<String>

        // BeforeAll ensure to create a fresh 'projects' object
        final def projects = projectContext.compute('projects', (k, v) -> new ConcurrentLinkedList<>()) as ConcurrentLinkedList
        final def project = new ConcurrentLinkedHashMap<>()
        project.name = "reports"
        projects.add(project)

        projectContext.put("project", project)

        project.reports = Utils.convertToConcurrent(templates.cards as List<Map<String, Object>>)
        // Each ID of cards template, needs to align with the exactly index of the reports templates list (card.id === templates.reports.idx)
        project.cardsFilesMap = Utils.convertToConcurrent(project.reports.collectEntries { it -> [(it.name): reportsTemplates[Integer.valueOf(it.id as String)].entrySet().first().getValue()] })
        return "OK"
    }
}